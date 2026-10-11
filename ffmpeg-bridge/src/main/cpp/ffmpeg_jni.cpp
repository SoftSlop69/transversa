// SPDX-License-Identifier: GPL-3.0-or-later
#include <jni.h>

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavcodec/version.h>
#include <libavformat/avformat.h>
#include <libavutil/avutil.h>
#include <libavutil/audio_fifo.h>
#include <libavutil/channel_layout.h>
#include <libavutil/dict.h>
#include <libavutil/imgutils.h>
#include <libavutil/mem.h>
#include <libavutil/samplefmt.h>
#include <libswresample/swresample.h>
#include <libswscale/swscale.h>
}

#include <cstdio>
#include <algorithm>
#include <cerrno>
#include <cstring>
#include <fcntl.h>
#include <memory>
#include <stdexcept>
#include <string>
#include <sys/syscall.h>
#include <linux/fs.h>
#include <sys/stat.h>
#include <vector>
#include <unistd.h>

namespace {
jstring toJavaString(JNIEnv* env, const char* value) {
    return env->NewStringUTF(value == nullptr ? "" : value);
}

std::string ffmpegError(int error) {
    char buffer[AV_ERROR_MAX_STRING_SIZE]{};
    av_strerror(error, buffer, sizeof(buffer));
    return buffer;
}

void requireFfmpeg(int result, const char* operation) {
    if (result < 0) {
        throw std::runtime_error(std::string(operation) + ": " + ffmpegError(result));
    }
}

class ProgressReporter {
public:
    ProgressReporter(JNIEnv* env, jobject listener) : env_(env), listener_(listener) {
        if (listener_ == nullptr) return;
        jclass listenerClass = env_->GetObjectClass(listener_);
        if (listenerClass != nullptr) {
            method_ = env_->GetMethodID(listenerClass, "onProgress", "(I)V");
            env_->DeleteLocalRef(listenerClass);
        }
        if (env_->ExceptionCheck()) env_->ExceptionClear();
    }

    void report(int percent) {
        percent = std::clamp(percent, 0, 100);
        if (percent <= lastPercent_) return;
        lastPercent_ = percent;
        if (method_ == nullptr || listener_ == nullptr) return;
        env_->CallVoidMethod(listener_, method_, static_cast<jint>(percent));
        if (env_->ExceptionCheck()) env_->ExceptionClear();
    }

private:
    JNIEnv* env_;
    jobject listener_;
    jmethodID method_ = nullptr;
    int lastPercent_ = -1;
};

void reportPacketProgress(ProgressReporter* progress, AVFormatContext* input, const AVPacket* packet) {
    if (progress == nullptr || input == nullptr || packet == nullptr || packet->stream_index < 0 ||
        static_cast<unsigned int>(packet->stream_index) >= input->nb_streams) return;
    const AVStream* stream = input->streams[packet->stream_index];
    const int64_t timestamp = packet->pts != AV_NOPTS_VALUE ? packet->pts : packet->dts;
    if (timestamp == AV_NOPTS_VALUE) return;

    int64_t durationUs = input->duration;
    int64_t startUs = input->start_time == AV_NOPTS_VALUE ? 0 : input->start_time;
    if (durationUs <= 0 && stream->duration > 0) {
        durationUs = av_rescale_q(stream->duration, stream->time_base, AV_TIME_BASE_Q);
        startUs = stream->start_time == AV_NOPTS_VALUE
            ? 0
            : av_rescale_q(stream->start_time, stream->time_base, AV_TIME_BASE_Q);
    }
    if (durationUs <= 0) return;
    const int64_t timestampUs = av_rescale_q(timestamp, stream->time_base, AV_TIME_BASE_Q);
    const int64_t elapsedUs = std::clamp<int64_t>(timestampUs - startUs, 0, durationUs);
    progress->report(static_cast<int>((elapsedUs * 100) / durationUs));
}

struct InputCloser {
    void operator()(AVFormatContext* context) const {
        if (context != nullptr) avformat_close_input(&context);
    }
};

struct DecoderCloser {
    void operator()(AVCodecContext* context) const { avcodec_free_context(&context); }
};

struct FrameCloser {
    void operator()(AVFrame* frame) const { av_frame_free(&frame); }
};

struct PacketCloser {
    void operator()(AVPacket* packet) const { av_packet_free(&packet); }
};

struct OutputCloser {
    void operator()(AVFormatContext* context) const {
        if (context == nullptr) return;
        if ((context->oformat->flags & AVFMT_NOFILE) == 0 && context->pb != nullptr) {
            avio_closep(&context->pb);
        }
        avformat_free_context(context);
    }
};

struct EncoderCloser {
    void operator()(AVCodecContext* context) const { avcodec_free_context(&context); }
};

struct ScaleCloser {
    void operator()(SwsContext* context) const { sws_freeContext(context); }
};

struct ResampleCloser {
    void operator()(SwrContext* context) const { swr_free(&context); }
};

struct AudioFifoCloser {
    void operator()(AVAudioFifo* fifo) const { av_audio_fifo_free(fifo); }
};

void flushResampler(SwrContext* resampler, AVCodecContext* encoder, AVAudioFifo* fifo) {
    for (;;) {
        const int capacity = swr_get_out_samples(resampler, 0);
        if (capacity <= 0) return;
        std::unique_ptr<AVFrame, FrameCloser> converted(av_frame_alloc());
        if (!converted) throw std::runtime_error("Could not allocate delayed audio frame.");
        converted->format = encoder->sample_fmt;
        converted->sample_rate = encoder->sample_rate;
        converted->nb_samples = capacity;
        requireFfmpeg(av_channel_layout_copy(&converted->ch_layout, &encoder->ch_layout), "Setting delayed audio layout");
        requireFfmpeg(av_frame_get_buffer(converted.get(), 0), "Allocating delayed audio frame");
        const int samples = swr_convert(resampler, converted->extended_data, capacity, nullptr, 0);
        requireFfmpeg(samples, "Flushing audio resampler");
        if (samples == 0) return;
        converted->nb_samples = samples;
        if (av_audio_fifo_write(fifo, reinterpret_cast<void**>(converted->extended_data), samples) != samples)
            throw std::runtime_error("Could not stage delayed audio samples.");
    }
}

void writeEncodedPackets(AVCodecContext* encoder, AVFormatContext* output, AVStream* stream) {
    std::unique_ptr<AVPacket, PacketCloser> packet(av_packet_alloc());
    if (!packet) throw std::runtime_error("Could not allocate audio packet.");
    for (;;) {
        const int result = avcodec_receive_packet(encoder, packet.get());
        if (result == AVERROR(EAGAIN) || result == AVERROR_EOF) break;
        requireFfmpeg(result, "Encoding audio");
        av_packet_rescale_ts(packet.get(), encoder->time_base, stream->time_base);
        packet->stream_index = stream->index;
        requireFfmpeg(av_interleaved_write_frame(output, packet.get()), "Writing audio output");
        av_packet_unref(packet.get());
    }
}

void encodeAudioFifo(AVAudioFifo* fifo, AVCodecContext* encoder, AVFormatContext* output,
                    AVStream* outputStream, int requestedSamples, int64_t* nextPts) {
    const int frameSize = encoder->frame_size > 0 ? encoder->frame_size : requestedSamples;
    while (av_audio_fifo_size(fifo) >= frameSize || (requestedSamples > 0 && av_audio_fifo_size(fifo) > 0)) {
        const int samples = std::min(frameSize, av_audio_fifo_size(fifo));
        std::unique_ptr<AVFrame, FrameCloser> frame(av_frame_alloc());
        if (!frame) throw std::runtime_error("Could not allocate encoded audio frame.");
        frame->format = encoder->sample_fmt;
        frame->sample_rate = encoder->sample_rate;
        frame->nb_samples = samples;
        requireFfmpeg(av_channel_layout_copy(&frame->ch_layout, &encoder->ch_layout), "Setting audio channel layout");
        requireFfmpeg(av_frame_get_buffer(frame.get(), 0), "Allocating encoded audio frame");
        const int read = av_audio_fifo_read(fifo, reinterpret_cast<void**>(frame->extended_data), samples);
        if (read != samples) throw std::runtime_error("Could not read staged audio samples.");
        frame->pts = *nextPts;
        *nextPts += samples;
        requireFfmpeg(avcodec_send_frame(encoder, frame.get()), "Submitting audio frame");
        writeEncodedPackets(encoder, output, outputStream);
    }
}

void convertAudioToMp3(const char* inputPath, const char* outputPath, int bitrate, ProgressReporter* progress) {
    AVFormatContext* rawInput = nullptr;
    requireFfmpeg(avformat_open_input(&rawInput, inputPath, nullptr, nullptr), "Opening input");
    std::unique_ptr<AVFormatContext, InputCloser> input(rawInput);
    requireFfmpeg(avformat_find_stream_info(input.get(), nullptr), "Reading input streams");
    const int audioIndex = av_find_best_stream(input.get(), AVMEDIA_TYPE_AUDIO, -1, -1, nullptr, 0);
    requireFfmpeg(audioIndex, "Finding audio stream");
    AVStream* inputStream = input->streams[audioIndex];
    const AVCodec* decoder = avcodec_find_decoder(inputStream->codecpar->codec_id);
    if (decoder == nullptr) throw std::runtime_error("No FFmpeg decoder is available for this audio.");
    std::unique_ptr<AVCodecContext, DecoderCloser> decoderContext(avcodec_alloc_context3(decoder));
    if (!decoderContext) throw std::runtime_error("Could not allocate the audio decoder.");
    requireFfmpeg(avcodec_parameters_to_context(decoderContext.get(), inputStream->codecpar), "Reading audio codec settings");
    requireFfmpeg(avcodec_open2(decoderContext.get(), decoder, nullptr), "Opening audio decoder");
    if (decoderContext->ch_layout.nb_channels == 0) {
        av_channel_layout_default(&decoderContext->ch_layout, 2);
    }

    AVFormatContext* rawOutput = nullptr;
    requireFfmpeg(avformat_alloc_output_context2(&rawOutput, nullptr, "mp3", outputPath), "Creating MP3 output");
    if (rawOutput == nullptr) throw std::runtime_error("Could not create MP3 output.");
    std::unique_ptr<AVFormatContext, OutputCloser> output(rawOutput);
    const AVCodec* encoder = avcodec_find_encoder_by_name("libmp3lame");
    if (encoder == nullptr) throw std::runtime_error("The bundled FFmpeg build has no MP3 encoder.");
    std::unique_ptr<AVCodecContext, EncoderCloser> encoderContext(avcodec_alloc_context3(encoder));
    if (!encoderContext) throw std::runtime_error("Could not allocate the MP3 encoder.");
    // 44.1 kHz is accepted by all MP3 encoders and avoids failures on unusual source rates.
    encoderContext->sample_rate = 44100;
    const void* supportedSampleFormats = nullptr;
    requireFfmpeg(avcodec_get_supported_config(nullptr, encoder, AV_CODEC_CONFIG_SAMPLE_FORMAT,
                                               0, &supportedSampleFormats, nullptr),
                  "Querying MP3 encoder sample formats");
    encoderContext->sample_fmt = supportedSampleFormats != nullptr
        ? static_cast<const AVSampleFormat*>(supportedSampleFormats)[0]
        : AV_SAMPLE_FMT_FLTP;
    encoderContext->bit_rate = bitrate;
    encoderContext->time_base = AVRational{1, encoderContext->sample_rate};
    const int channels = decoderContext->ch_layout.nb_channels > 0 ? decoderContext->ch_layout.nb_channels : 2;
    av_channel_layout_default(&encoderContext->ch_layout, std::min(channels, 2));
    if ((output->oformat->flags & AVFMT_GLOBALHEADER) != 0) encoderContext->flags |= AV_CODEC_FLAG_GLOBAL_HEADER;
    requireFfmpeg(avcodec_open2(encoderContext.get(), encoder, nullptr), "Opening MP3 encoder");

    AVStream* outputStream = avformat_new_stream(output.get(), nullptr);
    if (outputStream == nullptr) throw std::runtime_error("Could not create MP3 output stream.");
    outputStream->time_base = encoderContext->time_base;
    requireFfmpeg(avcodec_parameters_from_context(outputStream->codecpar, encoderContext.get()), "Setting MP3 output format");

    std::unique_ptr<SwrContext, ResampleCloser> resampler;
    SwrContext* rawResampler = nullptr;
    requireFfmpeg(swr_alloc_set_opts2(&rawResampler, &encoderContext->ch_layout, encoderContext->sample_fmt,
                                      encoderContext->sample_rate, &decoderContext->ch_layout,
                                      decoderContext->sample_fmt, decoderContext->sample_rate, 0, nullptr),
                  "Configuring audio resampling");
    resampler.reset(rawResampler);
    requireFfmpeg(swr_init(resampler.get()), "Starting audio resampling");
    std::unique_ptr<AVAudioFifo, AudioFifoCloser> fifo(
        av_audio_fifo_alloc(encoderContext->sample_fmt, encoderContext->ch_layout.nb_channels,
                            std::max(encoderContext->frame_size, 1) * 2));
    if (!fifo) throw std::runtime_error("Could not allocate audio staging buffer.");

    if ((output->oformat->flags & AVFMT_NOFILE) == 0) {
        requireFfmpeg(avio_open(&output->pb, outputPath, AVIO_FLAG_WRITE), "Opening MP3 output file");
    }
    requireFfmpeg(avformat_write_header(output.get(), nullptr), "Writing MP3 header");
    std::unique_ptr<AVPacket, PacketCloser> packet(av_packet_alloc());
    std::unique_ptr<AVFrame, FrameCloser> decoded(av_frame_alloc());
    if (!packet || !decoded) throw std::runtime_error("Could not allocate audio conversion buffers.");
    int64_t nextPts = 0;
    int readResult = 0;
    while ((readResult = av_read_frame(input.get(), packet.get())) >= 0) {
        if (packet->stream_index == audioIndex) {
            requireFfmpeg(avcodec_send_packet(decoderContext.get(), packet.get()), "Submitting audio packet");
            while (avcodec_receive_frame(decoderContext.get(), decoded.get()) == 0) {
                const int capacity = swr_get_out_samples(resampler.get(), decoded->nb_samples);
                std::unique_ptr<AVFrame, FrameCloser> converted(av_frame_alloc());
                if (!converted) throw std::runtime_error("Could not allocate resampled audio frame.");
                converted->format = encoderContext->sample_fmt;
                converted->sample_rate = encoderContext->sample_rate;
                converted->nb_samples = capacity;
                requireFfmpeg(av_channel_layout_copy(&converted->ch_layout, &encoderContext->ch_layout), "Setting resampled audio layout");
                requireFfmpeg(av_frame_get_buffer(converted.get(), 0), "Allocating resampled audio frame");
                const int samples = swr_convert(resampler.get(), converted->extended_data, capacity,
                                                const_cast<const uint8_t**>(decoded->extended_data), decoded->nb_samples);
                requireFfmpeg(samples, "Resampling audio");
                if (samples > 0) {
                    converted->nb_samples = samples;
                    const int written = av_audio_fifo_write(fifo.get(), reinterpret_cast<void**>(converted->extended_data), samples);
                    if (written != samples) throw std::runtime_error("Could not stage resampled audio.");
                    encodeAudioFifo(fifo.get(), encoderContext.get(), output.get(), outputStream, 0, &nextPts);
                }
                av_frame_unref(decoded.get());
            }
        }
        reportPacketProgress(progress, input.get(), packet.get());
        av_packet_unref(packet.get());
    }
    if (readResult != AVERROR_EOF) requireFfmpeg(readResult, "Reading audio input");
    requireFfmpeg(avcodec_send_packet(decoderContext.get(), nullptr), "Flushing audio decoder");
    while (avcodec_receive_frame(decoderContext.get(), decoded.get()) == 0) {
        const int capacity = swr_get_out_samples(resampler.get(), decoded->nb_samples);
        std::unique_ptr<AVFrame, FrameCloser> converted(av_frame_alloc());
        if (!converted) throw std::runtime_error("Could not allocate resampled audio frame.");
        converted->format = encoderContext->sample_fmt;
        converted->sample_rate = encoderContext->sample_rate;
        converted->nb_samples = capacity;
        requireFfmpeg(av_channel_layout_copy(&converted->ch_layout, &encoderContext->ch_layout), "Setting resampled audio layout");
        requireFfmpeg(av_frame_get_buffer(converted.get(), 0), "Allocating resampled audio frame");
        const int samples = swr_convert(resampler.get(), converted->extended_data, capacity,
                                        const_cast<const uint8_t**>(decoded->extended_data), decoded->nb_samples);
        requireFfmpeg(samples, "Resampling delayed audio");
        if (samples > 0) {
            converted->nb_samples = samples;
            if (av_audio_fifo_write(fifo.get(), reinterpret_cast<void**>(converted->extended_data), samples) != samples)
                throw std::runtime_error("Could not stage delayed audio.");
        }
        av_frame_unref(decoded.get());
    }
    flushResampler(resampler.get(), encoderContext.get(), fifo.get());
    encodeAudioFifo(fifo.get(), encoderContext.get(), output.get(), outputStream, 1, &nextPts);
    requireFfmpeg(avcodec_send_frame(encoderContext.get(), nullptr), "Flushing MP3 encoder");
    writeEncodedPackets(encoderContext.get(), output.get(), outputStream);
    requireFfmpeg(av_write_trailer(output.get()), "Finalizing MP3 output");
}

void writeVideoPackets(AVCodecContext* encoder, AVFormatContext* output, AVStream* stream) {
    std::unique_ptr<AVPacket, PacketCloser> packet(av_packet_alloc());
    if (!packet) throw std::runtime_error("Could not allocate video packet.");
    for (;;) {
        const int result = avcodec_receive_packet(encoder, packet.get());
        if (result == AVERROR(EAGAIN) || result == AVERROR_EOF) break;
        requireFfmpeg(result, "Encoding video");
        av_packet_rescale_ts(packet.get(), encoder->time_base, stream->time_base);
        packet->stream_index = stream->index;
        requireFfmpeg(av_interleaved_write_frame(output, packet.get()), "Writing video output");
        av_packet_unref(packet.get());
    }
}

void convertVideoToMp4(const char* inputPath, const char* outputPath, int videoBitrate,
                       int crf, int audioBitrate, bool mediumPreset, ProgressReporter* progress) {
    AVFormatContext* rawInput = nullptr;
    requireFfmpeg(avformat_open_input(&rawInput, inputPath, nullptr, nullptr), "Opening input");
    std::unique_ptr<AVFormatContext, InputCloser> input(rawInput);
    requireFfmpeg(avformat_find_stream_info(input.get(), nullptr), "Reading input streams");
    const int videoIndex = av_find_best_stream(input.get(), AVMEDIA_TYPE_VIDEO, -1, -1, nullptr, 0);
    requireFfmpeg(videoIndex, "Finding video stream");
    const int audioIndex = av_find_best_stream(input.get(), AVMEDIA_TYPE_AUDIO, -1, videoIndex, nullptr, 0);

    AVStream* inputVideo = input->streams[videoIndex];
    const AVCodec* videoDecoder = avcodec_find_decoder(inputVideo->codecpar->codec_id);
    if (videoDecoder == nullptr) throw std::runtime_error("No FFmpeg decoder is available for this video.");
    std::unique_ptr<AVCodecContext, DecoderCloser> videoDecoderContext(avcodec_alloc_context3(videoDecoder));
    if (!videoDecoderContext) throw std::runtime_error("Could not allocate the video decoder.");
    requireFfmpeg(avcodec_parameters_to_context(videoDecoderContext.get(), inputVideo->codecpar), "Reading video codec settings");
    requireFfmpeg(avcodec_open2(videoDecoderContext.get(), videoDecoder, nullptr), "Opening video decoder");

    AVFormatContext* rawOutput = nullptr;
    requireFfmpeg(avformat_alloc_output_context2(&rawOutput, nullptr, "mp4", outputPath), "Creating MP4 output");
    if (rawOutput == nullptr) throw std::runtime_error("Could not create MP4 output.");
    std::unique_ptr<AVFormatContext, OutputCloser> output(rawOutput);

    const AVCodec* videoEncoder = avcodec_find_encoder_by_name("libx264");
    if (videoEncoder == nullptr) throw std::runtime_error("The bundled FFmpeg build has no H.264 software encoder.");
    std::unique_ptr<AVCodecContext, EncoderCloser> videoEncoderContext(avcodec_alloc_context3(videoEncoder));
    if (!videoEncoderContext) throw std::runtime_error("Could not allocate the H.264 encoder.");
    videoEncoderContext->width = videoDecoderContext->width & ~1;
    videoEncoderContext->height = videoDecoderContext->height & ~1;
    if (videoEncoderContext->width < 2 || videoEncoderContext->height < 2)
        throw std::runtime_error("Video dimensions are too small for H.264 encoding.");
    videoEncoderContext->pix_fmt = AV_PIX_FMT_YUV420P;
    AVRational frameRate = av_guess_frame_rate(input.get(), inputVideo, nullptr);
    if (frameRate.num <= 0 || frameRate.den <= 0) frameRate = AVRational{30, 1};
    videoEncoderContext->framerate = frameRate;
    videoEncoderContext->time_base = av_inv_q(frameRate);
    videoEncoderContext->bit_rate = videoBitrate;
    videoEncoderContext->gop_size = std::max(1, frameRate.num / frameRate.den * 2);
    videoEncoderContext->max_b_frames = 0;
    if ((output->oformat->flags & AVFMT_GLOBALHEADER) != 0) videoEncoderContext->flags |= AV_CODEC_FLAG_GLOBAL_HEADER;
    AVDictionary* rawVideoOptions = nullptr;
    av_dict_set(&rawVideoOptions, "preset", mediumPreset ? "medium" : "veryfast", 0);
    const std::string crfText = std::to_string(crf);
    av_dict_set(&rawVideoOptions, "crf", crfText.c_str(), 0);
    const int videoEncoderResult = avcodec_open2(videoEncoderContext.get(), videoEncoder, &rawVideoOptions);
    av_dict_free(&rawVideoOptions);
    requireFfmpeg(videoEncoderResult, "Opening H.264 encoder");
    AVStream* outputVideo = avformat_new_stream(output.get(), nullptr);
    if (outputVideo == nullptr) throw std::runtime_error("Could not create MP4 video stream.");
    outputVideo->time_base = videoEncoderContext->time_base;
    requireFfmpeg(avcodec_parameters_from_context(outputVideo->codecpar, videoEncoderContext.get()), "Setting MP4 video format");

    std::unique_ptr<AVCodecContext, DecoderCloser> audioDecoderContext;
    std::unique_ptr<AVCodecContext, EncoderCloser> audioEncoderContext;
    std::unique_ptr<SwrContext, ResampleCloser> audioResampler;
    std::unique_ptr<AVAudioFifo, AudioFifoCloser> audioFifo;
    AVStream* outputAudio = nullptr;
    if (audioIndex >= 0) {
        AVStream* inputAudio = input->streams[audioIndex];
        const AVCodec* audioDecoder = avcodec_find_decoder(inputAudio->codecpar->codec_id);
        if (audioDecoder != nullptr) {
            audioDecoderContext.reset(avcodec_alloc_context3(audioDecoder));
            if (!audioDecoderContext) throw std::runtime_error("Could not allocate the audio decoder.");
            requireFfmpeg(avcodec_parameters_to_context(audioDecoderContext.get(), inputAudio->codecpar), "Reading video audio settings");
            requireFfmpeg(avcodec_open2(audioDecoderContext.get(), audioDecoder, nullptr), "Opening video audio decoder");
            if (audioDecoderContext->ch_layout.nb_channels == 0) av_channel_layout_default(&audioDecoderContext->ch_layout, 2);

            const AVCodec* audioEncoder = avcodec_find_encoder(AV_CODEC_ID_AAC);
            if (audioEncoder == nullptr) throw std::runtime_error("The bundled FFmpeg build has no AAC encoder.");
            audioEncoderContext.reset(avcodec_alloc_context3(audioEncoder));
            if (!audioEncoderContext) throw std::runtime_error("Could not allocate the AAC encoder.");
            audioEncoderContext->sample_rate = audioDecoderContext->sample_rate > 0 ? audioDecoderContext->sample_rate : 44100;
            const void* sampleFormats = nullptr;
            requireFfmpeg(avcodec_get_supported_config(nullptr, audioEncoder, AV_CODEC_CONFIG_SAMPLE_FORMAT,
                                                       0, &sampleFormats, nullptr), "Querying AAC encoder sample formats");
            audioEncoderContext->sample_fmt = sampleFormats != nullptr
                ? static_cast<const AVSampleFormat*>(sampleFormats)[0]
                : AV_SAMPLE_FMT_FLTP;
            audioEncoderContext->bit_rate = audioBitrate;
            audioEncoderContext->time_base = AVRational{1, audioEncoderContext->sample_rate};
            av_channel_layout_default(&audioEncoderContext->ch_layout,
                                      std::min(audioDecoderContext->ch_layout.nb_channels, 2));
            if ((output->oformat->flags & AVFMT_GLOBALHEADER) != 0) audioEncoderContext->flags |= AV_CODEC_FLAG_GLOBAL_HEADER;
            requireFfmpeg(avcodec_open2(audioEncoderContext.get(), audioEncoder, nullptr), "Opening AAC encoder");
            outputAudio = avformat_new_stream(output.get(), nullptr);
            if (outputAudio == nullptr) throw std::runtime_error("Could not create MP4 audio stream.");
            outputAudio->time_base = audioEncoderContext->time_base;
            requireFfmpeg(avcodec_parameters_from_context(outputAudio->codecpar, audioEncoderContext.get()), "Setting MP4 audio format");

            SwrContext* rawResampler = nullptr;
            requireFfmpeg(swr_alloc_set_opts2(&rawResampler, &audioEncoderContext->ch_layout,
                                              audioEncoderContext->sample_fmt, audioEncoderContext->sample_rate,
                                              &audioDecoderContext->ch_layout, audioDecoderContext->sample_fmt,
                                              audioDecoderContext->sample_rate, 0, nullptr),
                          "Configuring video audio resampling");
            audioResampler.reset(rawResampler);
            requireFfmpeg(swr_init(audioResampler.get()), "Starting video audio resampling");
            audioFifo.reset(av_audio_fifo_alloc(audioEncoderContext->sample_fmt,
                                                audioEncoderContext->ch_layout.nb_channels,
                                                std::max(audioEncoderContext->frame_size, 1) * 2));
            if (!audioFifo) throw std::runtime_error("Could not allocate video audio staging buffer.");
        }
    }

    std::unique_ptr<SwsContext, ScaleCloser> scaler(sws_getContext(
        videoDecoderContext->width, videoDecoderContext->height, videoDecoderContext->pix_fmt,
        videoEncoderContext->width, videoEncoderContext->height, videoEncoderContext->pix_fmt,
        SWS_BICUBIC, nullptr, nullptr, nullptr));
    if (!scaler) throw std::runtime_error("Could not initialize video pixel conversion.");
    std::unique_ptr<AVFrame, FrameCloser> decodedVideo(av_frame_alloc());
    std::unique_ptr<AVFrame, FrameCloser> encodedVideo(av_frame_alloc());
    std::unique_ptr<AVPacket, PacketCloser> packet(av_packet_alloc());
    if (!decodedVideo || !encodedVideo || !packet) throw std::runtime_error("Could not allocate video conversion buffers.");
    encodedVideo->format = videoEncoderContext->pix_fmt;
    encodedVideo->width = videoEncoderContext->width;
    encodedVideo->height = videoEncoderContext->height;
    requireFfmpeg(av_frame_get_buffer(encodedVideo.get(), 32), "Allocating H.264 frame");
    if ((output->oformat->flags & AVFMT_NOFILE) == 0)
        requireFfmpeg(avio_open(&output->pb, outputPath, AVIO_FLAG_WRITE), "Opening MP4 output file");
    requireFfmpeg(avformat_write_header(output.get(), nullptr), "Writing MP4 header");

    int64_t audioPts = 0;
    int64_t videoFrames = 0;
    int readResult = 0;
    while ((readResult = av_read_frame(input.get(), packet.get())) >= 0) {
        if (packet->stream_index == videoIndex) {
            requireFfmpeg(avcodec_send_packet(videoDecoderContext.get(), packet.get()), "Submitting video packet");
            while (avcodec_receive_frame(videoDecoderContext.get(), decodedVideo.get()) == 0) {
                requireFfmpeg(av_frame_make_writable(encodedVideo.get()), "Preparing H.264 frame");
                const int rows = sws_scale(scaler.get(), decodedVideo->data, decodedVideo->linesize, 0,
                                           videoDecoderContext->height, encodedVideo->data, encodedVideo->linesize);
                if (rows != videoEncoderContext->height) throw std::runtime_error("Could not convert video pixels.");
                const int64_t sourcePts = decodedVideo->best_effort_timestamp;
                encodedVideo->pts = sourcePts == AV_NOPTS_VALUE
                    ? videoFrames
                    : av_rescale_q(sourcePts, inputVideo->time_base, videoEncoderContext->time_base);
                if (videoFrames > 0 && encodedVideo->pts <= videoFrames - 1) encodedVideo->pts = videoFrames;
                ++videoFrames;
                requireFfmpeg(avcodec_send_frame(videoEncoderContext.get(), encodedVideo.get()), "Submitting H.264 frame");
                writeVideoPackets(videoEncoderContext.get(), output.get(), outputVideo);
                av_frame_unref(decodedVideo.get());
            }
        } else if (audioDecoderContext && packet->stream_index == audioIndex) {
            requireFfmpeg(avcodec_send_packet(audioDecoderContext.get(), packet.get()), "Submitting video audio packet");
            std::unique_ptr<AVFrame, FrameCloser> decodedAudio(av_frame_alloc());
            if (!decodedAudio) throw std::runtime_error("Could not allocate video audio frame.");
            while (avcodec_receive_frame(audioDecoderContext.get(), decodedAudio.get()) == 0) {
                const int capacity = swr_get_out_samples(audioResampler.get(), decodedAudio->nb_samples);
                std::unique_ptr<AVFrame, FrameCloser> converted(av_frame_alloc());
                if (!converted) throw std::runtime_error("Could not allocate resampled video audio frame.");
                converted->format = audioEncoderContext->sample_fmt;
                converted->sample_rate = audioEncoderContext->sample_rate;
                converted->nb_samples = capacity;
                requireFfmpeg(av_channel_layout_copy(&converted->ch_layout, &audioEncoderContext->ch_layout), "Setting video audio layout");
                requireFfmpeg(av_frame_get_buffer(converted.get(), 0), "Allocating resampled video audio frame");
                const int samples = swr_convert(audioResampler.get(), converted->extended_data, capacity,
                                                const_cast<const uint8_t**>(decodedAudio->extended_data), decodedAudio->nb_samples);
                requireFfmpeg(samples, "Resampling video audio");
                if (samples > 0) {
                    converted->nb_samples = samples;
                    if (av_audio_fifo_write(audioFifo.get(), reinterpret_cast<void**>(converted->extended_data), samples) != samples)
                        throw std::runtime_error("Could not stage video audio samples.");
                    encodeAudioFifo(audioFifo.get(), audioEncoderContext.get(), output.get(), outputAudio, 0, &audioPts);
                }
                av_frame_unref(decodedAudio.get());
            }
        }
        reportPacketProgress(progress, input.get(), packet.get());
        av_packet_unref(packet.get());
    }
    if (readResult != AVERROR_EOF) requireFfmpeg(readResult, "Reading video input");
    if (videoFrames == 0) throw std::runtime_error("No decodable video frames were found.");
    requireFfmpeg(avcodec_send_packet(videoDecoderContext.get(), nullptr), "Flushing video decoder");
    while (avcodec_receive_frame(videoDecoderContext.get(), decodedVideo.get()) == 0) {
        requireFfmpeg(av_frame_make_writable(encodedVideo.get()), "Preparing final H.264 frame");
        const int rows = sws_scale(scaler.get(), decodedVideo->data, decodedVideo->linesize, 0,
                                   videoDecoderContext->height, encodedVideo->data, encodedVideo->linesize);
        if (rows != videoEncoderContext->height) throw std::runtime_error("Could not convert final video frame.");
        encodedVideo->pts = videoFrames++;
        requireFfmpeg(avcodec_send_frame(videoEncoderContext.get(), encodedVideo.get()), "Submitting final H.264 frame");
        writeVideoPackets(videoEncoderContext.get(), output.get(), outputVideo);
        av_frame_unref(decodedVideo.get());
    }
    requireFfmpeg(avcodec_send_frame(videoEncoderContext.get(), nullptr), "Flushing H.264 encoder");
    writeVideoPackets(videoEncoderContext.get(), output.get(), outputVideo);
    if (audioDecoderContext) {
        requireFfmpeg(avcodec_send_packet(audioDecoderContext.get(), nullptr), "Flushing video audio decoder");
        std::unique_ptr<AVFrame, FrameCloser> decodedAudio(av_frame_alloc());
        if (!decodedAudio) throw std::runtime_error("Could not allocate final video audio frame.");
        while (avcodec_receive_frame(audioDecoderContext.get(), decodedAudio.get()) == 0) {
            const int capacity = swr_get_out_samples(audioResampler.get(), decodedAudio->nb_samples);
            std::unique_ptr<AVFrame, FrameCloser> converted(av_frame_alloc());
            if (!converted) throw std::runtime_error("Could not allocate final resampled audio frame.");
            converted->format = audioEncoderContext->sample_fmt;
            converted->sample_rate = audioEncoderContext->sample_rate;
            converted->nb_samples = capacity;
            requireFfmpeg(av_channel_layout_copy(&converted->ch_layout, &audioEncoderContext->ch_layout), "Setting final audio layout");
            requireFfmpeg(av_frame_get_buffer(converted.get(), 0), "Allocating final resampled audio frame");
            const int samples = swr_convert(audioResampler.get(), converted->extended_data, capacity,
                                            const_cast<const uint8_t**>(decodedAudio->extended_data), decodedAudio->nb_samples);
            requireFfmpeg(samples, "Resampling final video audio");
            if (samples > 0) {
                converted->nb_samples = samples;
                if (av_audio_fifo_write(audioFifo.get(), reinterpret_cast<void**>(converted->extended_data), samples) != samples)
                    throw std::runtime_error("Could not stage final video audio samples.");
            }
            av_frame_unref(decodedAudio.get());
        }
        flushResampler(audioResampler.get(), audioEncoderContext.get(), audioFifo.get());
        encodeAudioFifo(audioFifo.get(), audioEncoderContext.get(), output.get(), outputAudio, 1, &audioPts);
        requireFfmpeg(avcodec_send_frame(audioEncoderContext.get(), nullptr), "Flushing AAC encoder");
        writeEncodedPackets(audioEncoderContext.get(), output.get(), outputAudio);
    }
    requireFfmpeg(av_write_trailer(output.get()), "Finalizing MP4 output");
}

template <typename Conversion>
void convertTransactionally(const char* inputPath, const char* outputPath,
                            Conversion conversion, AVMediaType expectedMediaType) {
    const std::string temporaryPath = std::string(outputPath) + ".transversa-partial";
    for (const char* path : {outputPath, temporaryPath.c_str()}) {
        errno = 0;
        if (access(path, F_OK) == 0) {
            throw std::runtime_error(std::string("Output path already exists: ") + path);
        }
        if (errno != ENOENT) {
            throw std::runtime_error(std::string("Could not safely check output path: ") + std::strerror(errno));
        }
    }
    try {
        conversion(inputPath, temporaryPath.c_str());
        struct stat outputStat {};
        if (stat(temporaryPath.c_str(), &outputStat) != 0 || outputStat.st_size <= 0) {
            throw std::runtime_error("FFmpeg produced an empty output file.");
        }
        AVFormatContext* rawValidation = nullptr;
        requireFfmpeg(avformat_open_input(&rawValidation, temporaryPath.c_str(), nullptr, nullptr),
                      "Validating converted output");
        std::unique_ptr<AVFormatContext, InputCloser> validation(rawValidation);
        requireFfmpeg(avformat_find_stream_info(validation.get(), nullptr), "Reading converted output streams");
        bool hasExpectedStream = false;
        for (unsigned int index = 0; index < validation->nb_streams; ++index) {
            if (validation->streams[index]->codecpar->codec_type == expectedMediaType) {
                hasExpectedStream = true;
                break;
            }
        }
        if (!hasExpectedStream) throw std::runtime_error("Converted output did not contain the expected media stream.");
        bool committed = false;
#if defined(SYS_renameat2) && defined(RENAME_NOREPLACE)
        if (syscall(SYS_renameat2, AT_FDCWD, temporaryPath.c_str(), AT_FDCWD, outputPath,
                    RENAME_NOREPLACE) == 0) {
            committed = true;
        } else if (errno != ENOSYS && errno != EINVAL) {
            throw std::runtime_error(std::string("Could not commit completed output: ") + std::strerror(errno));
        }
#endif
        if (!committed) {
            // Some older Android kernels lack renameat2. Reserve the private app path
            // exclusively, then replace our own reservation with the completed file.
            const int reservation = open(outputPath, O_CREAT | O_EXCL | O_WRONLY | O_CLOEXEC, 0600);
            if (reservation < 0) {
                throw std::runtime_error(std::string("Could not reserve output path: ") + std::strerror(errno));
            }
            close(reservation);
            if (rename(temporaryPath.c_str(), outputPath) != 0) {
                const int renameError = errno;
                std::remove(outputPath);
                throw std::runtime_error(std::string("Could not commit completed output: ") + std::strerror(renameError));
            }
        }
    } catch (...) {
        std::remove(temporaryPath.c_str());
        throw;
    }
}

void convertImageToPng(const char* inputPath, const char* outputPath, int compressionLevel, ProgressReporter* progress) {
    if (progress != nullptr) progress->report(20);
    AVFormatContext* rawInput = nullptr;
    requireFfmpeg(avformat_open_input(&rawInput, inputPath, nullptr, nullptr), "Opening input");
    std::unique_ptr<AVFormatContext, InputCloser> input(rawInput);
    requireFfmpeg(avformat_find_stream_info(input.get(), nullptr), "Reading input streams");

    const int videoIndex = av_find_best_stream(input.get(), AVMEDIA_TYPE_VIDEO, -1, -1, nullptr, 0);
    requireFfmpeg(videoIndex, "Finding image/video stream");
    AVStream* inputStream = input->streams[videoIndex];
    const AVCodec* decoder = avcodec_find_decoder(inputStream->codecpar->codec_id);
    if (decoder == nullptr) throw std::runtime_error("No FFmpeg decoder is available for this image.");

    std::unique_ptr<AVCodecContext, DecoderCloser> decoderContext(avcodec_alloc_context3(decoder));
    if (!decoderContext) throw std::runtime_error("Could not allocate the image decoder.");
    requireFfmpeg(avcodec_parameters_to_context(decoderContext.get(), inputStream->codecpar), "Reading image codec settings");
    requireFfmpeg(avcodec_open2(decoderContext.get(), decoder, nullptr), "Opening image decoder");

    AVFormatContext* rawOutput = nullptr;
    requireFfmpeg(avformat_alloc_output_context2(&rawOutput, nullptr, "image2pipe", outputPath), "Creating PNG output");
    if (rawOutput == nullptr) throw std::runtime_error("Could not create PNG output.");
    std::unique_ptr<AVFormatContext, OutputCloser> output(rawOutput);

    const AVCodec* encoder = avcodec_find_encoder(AV_CODEC_ID_PNG);
    if (encoder == nullptr) throw std::runtime_error("The bundled FFmpeg build has no PNG encoder.");
    std::unique_ptr<AVCodecContext, EncoderCloser> encoderContext(avcodec_alloc_context3(encoder));
    if (!encoderContext) throw std::runtime_error("Could not allocate the PNG encoder.");
    encoderContext->width = decoderContext->width;
    encoderContext->height = decoderContext->height;
    encoderContext->pix_fmt = AV_PIX_FMT_RGBA;
    encoderContext->time_base = AVRational{1, 1};
    AVDictionary* rawPngOptions = nullptr;
    const std::string compressionText = std::to_string(compressionLevel);
    av_dict_set(&rawPngOptions, "compression_level", compressionText.c_str(), 0);
    const int pngEncoderResult = avcodec_open2(encoderContext.get(), encoder, &rawPngOptions);
    av_dict_free(&rawPngOptions);
    requireFfmpeg(pngEncoderResult, "Opening PNG encoder");

    AVStream* outputStream = avformat_new_stream(output.get(), nullptr);
    if (outputStream == nullptr) throw std::runtime_error("Could not create the PNG output stream.");
    outputStream->time_base = encoderContext->time_base;
    requireFfmpeg(avcodec_parameters_from_context(outputStream->codecpar, encoderContext.get()), "Setting PNG output format");

    if ((output->oformat->flags & AVFMT_NOFILE) == 0) {
        requireFfmpeg(avio_open(&output->pb, outputPath, AVIO_FLAG_WRITE), "Opening PNG output file");
    }
    requireFfmpeg(avformat_write_header(output.get(), nullptr), "Writing PNG header");

    std::unique_ptr<AVFrame, FrameCloser> decoded(av_frame_alloc());
    std::unique_ptr<AVFrame, FrameCloser> rgba(av_frame_alloc());
    std::unique_ptr<AVPacket, PacketCloser> packet(av_packet_alloc());
    std::unique_ptr<AVPacket, PacketCloser> encoded(av_packet_alloc());
    if (!decoded || !rgba || !packet || !encoded) throw std::runtime_error("Could not allocate FFmpeg image buffers.");
    rgba->format = encoderContext->pix_fmt;
    rgba->width = encoderContext->width;
    rgba->height = encoderContext->height;
    requireFfmpeg(av_frame_get_buffer(rgba.get(), 32), "Allocating PNG frame");

    std::unique_ptr<SwsContext, ScaleCloser> scaler(
        sws_getContext(decoderContext->width, decoderContext->height, decoderContext->pix_fmt,
                       encoderContext->width, encoderContext->height, encoderContext->pix_fmt,
                       SWS_BICUBIC, nullptr, nullptr, nullptr));
    if (!scaler) throw std::runtime_error("Could not initialize image pixel conversion.");

    bool wroteImage = false;
    while (!wroteImage && av_read_frame(input.get(), packet.get()) >= 0) {
        if (packet->stream_index == videoIndex) {
            requireFfmpeg(avcodec_send_packet(decoderContext.get(), packet.get()), "Decoding image");
            while (avcodec_receive_frame(decoderContext.get(), decoded.get()) == 0) {
                requireFfmpeg(av_frame_make_writable(rgba.get()), "Preparing PNG frame");
                const int rows = sws_scale(scaler.get(), decoded->data, decoded->linesize, 0,
                                           decoderContext->height, rgba->data, rgba->linesize);
                if (rows != encoderContext->height) throw std::runtime_error("Could not convert image pixels to PNG.");
                rgba->pts = 0;
                requireFfmpeg(avcodec_send_frame(encoderContext.get(), rgba.get()), "Encoding PNG");
                const int receiveResult = avcodec_receive_packet(encoderContext.get(), encoded.get());
                requireFfmpeg(receiveResult, "Finishing PNG encoding");
                encoded->stream_index = outputStream->index;
                av_packet_rescale_ts(encoded.get(), encoderContext->time_base, outputStream->time_base);
                requireFfmpeg(av_interleaved_write_frame(output.get(), encoded.get()), "Writing PNG image");
                av_packet_unref(encoded.get());
                av_frame_unref(decoded.get());
                wroteImage = true;
                break;
            }
        }
        av_packet_unref(packet.get());
    }
    if (!wroteImage) throw std::runtime_error("No decodable image frame was found.");
    if (progress != nullptr) progress->report(80);
    requireFfmpeg(av_write_trailer(output.get()), "Finalizing PNG output");
    if (progress != nullptr) progress->report(95);
}

jobjectArray encoderNames(JNIEnv* env) {
    std::vector<std::string> names;
    void* cursor = nullptr;
    while (const AVCodec* codec = av_codec_iterate(&cursor)) {
        if (av_codec_is_encoder(codec) != 0) {
            names.emplace_back(codec->name);
        }
    }

    jclass stringClass = env->FindClass("java/lang/String");
    if (stringClass == nullptr) {
        return nullptr;
    }
    jobjectArray result = env->NewObjectArray(static_cast<jsize>(names.size()), stringClass, nullptr);
    if (result == nullptr) {
        return nullptr;
    }
    for (jsize index = 0; index < static_cast<jsize>(names.size()); ++index) {
        jstring name = env->NewStringUTF(names[index].c_str());
        if (name == nullptr) {
            return nullptr;
        }
        env->SetObjectArrayElement(result, index, name);
        env->DeleteLocalRef(name);
    }
    return result;
}
}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_transversa_ffmpeg_FfmpegBridge_nativeVersion(JNIEnv* env, jobject /* this */) {
    return toJavaString(env, av_version_info());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_transversa_ffmpeg_FfmpegBridge_nativeConfiguration(JNIEnv* env, jobject /* this */) {
    return toJavaString(env, avcodec_configuration());
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_transversa_ffmpeg_FfmpegBridge_nativeEncoders(JNIEnv* env, jobject /* this */) {
    return encoderNames(env);
}

extern "C" JNIEXPORT void JNICALL
Java_com_transversa_ffmpeg_FfmpegBridge_nativeConvertImageToPng(JNIEnv* env, jobject /* this */,
                                                                 jstring inputPath, jstring outputPath,
                                                                 jint compressionLevel, jobject progressListener) {
    if (inputPath == nullptr || outputPath == nullptr) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Input and output paths are required.");
        return;
    }
    const char* input = env->GetStringUTFChars(inputPath, nullptr);
    const char* output = env->GetStringUTFChars(outputPath, nullptr);
    if (input == nullptr || output == nullptr) {
        if (input != nullptr) env->ReleaseStringUTFChars(inputPath, input);
        if (output != nullptr) env->ReleaseStringUTFChars(outputPath, output);
        return;
    }
    try {
        ProgressReporter reporter(env, progressListener);
        convertTransactionally(input, output,
                               [compressionLevel, &reporter](const char* source, const char* destination) {
                                   convertImageToPng(source, destination, compressionLevel, &reporter);
                               },
                               AVMEDIA_TYPE_VIDEO);
        reporter.report(100);
    } catch (const std::exception& error) {
        jclass exceptionClass = env->FindClass("com/transversa/ffmpeg/FfmpegConversionException");
        if (exceptionClass != nullptr) env->ThrowNew(exceptionClass, error.what());
    }
    env->ReleaseStringUTFChars(inputPath, input);
    env->ReleaseStringUTFChars(outputPath, output);
}


extern "C" JNIEXPORT void JNICALL
Java_com_transversa_ffmpeg_FfmpegBridge_nativeConvertAudioToMp3(JNIEnv* env, jobject /* this */,
                                                                 jstring inputPath, jstring outputPath,
                                                                 jint bitrate, jobject progressListener) {
    if (inputPath == nullptr || outputPath == nullptr) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Input and output paths are required.");
        return;
    }
    const char* input = env->GetStringUTFChars(inputPath, nullptr);
    const char* output = env->GetStringUTFChars(outputPath, nullptr);
    if (input == nullptr || output == nullptr) {
        if (input != nullptr) env->ReleaseStringUTFChars(inputPath, input);
        if (output != nullptr) env->ReleaseStringUTFChars(outputPath, output);
        return;
    }
    try {
        ProgressReporter reporter(env, progressListener);
        convertTransactionally(input, output,
                               [bitrate, &reporter](const char* source, const char* destination) {
                                   convertAudioToMp3(source, destination, bitrate, &reporter);
                               },
                               AVMEDIA_TYPE_AUDIO);
        reporter.report(100);
    } catch (const std::exception& error) {
        jclass exceptionClass = env->FindClass("com/transversa/ffmpeg/FfmpegConversionException");
        if (exceptionClass != nullptr) env->ThrowNew(exceptionClass, error.what());
    }
    env->ReleaseStringUTFChars(inputPath, input);
    env->ReleaseStringUTFChars(outputPath, output);
}

extern "C" JNIEXPORT void JNICALL
Java_com_transversa_ffmpeg_FfmpegBridge_nativeConvertVideoToMp4(JNIEnv* env, jobject /* this */,
                                                                 jstring inputPath, jstring outputPath,
                                                                 jint videoBitrate, jint crf,
                                                                 jint audioBitrate, jboolean mediumPreset,
                                                                 jobject progressListener) {
    if (inputPath == nullptr || outputPath == nullptr) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Input and output paths are required.");
        return;
    }
    const char* input = env->GetStringUTFChars(inputPath, nullptr);
    const char* output = env->GetStringUTFChars(outputPath, nullptr);
    if (input == nullptr || output == nullptr) {
        if (input != nullptr) env->ReleaseStringUTFChars(inputPath, input);
        if (output != nullptr) env->ReleaseStringUTFChars(outputPath, output);
        return;
    }
    try {
        ProgressReporter reporter(env, progressListener);
        convertTransactionally(input, output,
                               [videoBitrate, crf, audioBitrate, mediumPreset, &reporter](const char* source,
                                                                                          const char* destination) {
                                   convertVideoToMp4(source, destination, videoBitrate, crf,
                                                     audioBitrate, mediumPreset == JNI_TRUE, &reporter);
                               },
                               AVMEDIA_TYPE_VIDEO);
        reporter.report(100);
    } catch (const std::exception& error) {
        jclass exceptionClass = env->FindClass("com/transversa/ffmpeg/FfmpegConversionException");
        if (exceptionClass != nullptr) env->ThrowNew(exceptionClass, error.what());
    }
    env->ReleaseStringUTFChars(inputPath, input);
    env->ReleaseStringUTFChars(outputPath, output);
}
