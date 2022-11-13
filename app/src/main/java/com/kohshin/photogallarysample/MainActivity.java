package com.kohshin.photogallarysample;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextView textView;

    private static final String TAG = "MainActivity";

    /** バッファを取り出す際のタイムアウト時間(マイクロ秒) */
    private static final long TIMEOUT_US = 1;

    String mFilePath;
    private MediaMuxer mMuxer;
    MediaFormat format;

    ActivityResultLauncher<Intent> resultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent resultData  = result.getData();
                    if (resultData  != null) {
                        openImage(resultData);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textView = findViewById(R.id.text_view);

        Button button = findViewById(R.id.button);
        button.setOnClickListener( v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("video/*");

            resultLauncher.launch(intent);
        });
    }

    void openImage(Intent resultData){
        Uri uri = resultData.getData();
        // Uriを表示
        textView.setText( String.format(Locale.US, "Uri:　%s",uri.toString()));

        SaveExecute();

        // assetsからメディアファイルを読み込む
//        AssetFileDescriptor descriptor = getResources().openRawResourceFd( R.raw.sample);

        // MediaExtractorでファイルからフォーマット情報を抽出。
        MediaExtractor extractor = new MediaExtractor();
        try {
//            extractor.setDataSource(descriptor.getFileDescriptor(), descriptor.getStartOffset(), descriptor.getLength());
            extractor.setDataSource(getApplicationContext(), uri, null);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // トラック数
        // 動画の場合、トラック1が映像、トラック2が音声？
        Log.d(TAG, String.format("TRACKS #: %d", extractor.getTrackCount()));

        // 音声のMime Type
        format = extractor.getTrackFormat(1);
        String mime = format.getString(MediaFormat.KEY_MIME);
        Log.d(TAG, String.format("Audio MIME TYPE: %s", mime));



        // デコーターを作成する
        MediaCodec codec = null;
        try {
            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(format, null, null, 0);
            codec.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // デコーダーからInputBuffer, OutputBufferを取得する
        ByteBuffer[] codecInputBuffers;
        ByteBuffer[] codecOutputBuffers;
        codecInputBuffers = codec.getInputBuffers();
        codecOutputBuffers = codec.getOutputBuffers();


        // 読み込むトラック番号を指定する
        // ここでは音声を指定
        extractor.selectTrack(1);

        // AudioTrac生成用にメディアから情報取得
        // サンプリングレート
        int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        // バッファの最大バイトサイズ
        int maxSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);

        // インプットバッファがEnd Of Streamかどうかを判定するフラグ
        boolean sawInputEOS = false;

        //エンコーダー設定
        MediaFormat format_encoder = MediaFormat.createAudioFormat("audio/3gpp", 48000, 1);
        format_encoder.setInteger(MediaFormat.KEY_BIT_RATE, 48000);
        format_encoder.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1024);
        MediaCodec encoder = null;
        try {
            encoder = MediaCodec.createEncoderByType("audio/3gpp");
        } catch (IOException e) {
            e.printStackTrace();
        }
        encoder.configure(format_encoder, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        encoder.start();


        int auioTrackIndex = mMuxer.addTrack(format_encoder);

        // エンコーダーからInputBuffer, OutputBufferを取得する
        ByteBuffer[] codecInputBuffers_encoder;
        ByteBuffer[] codecOutputBuffers_encoder;
        codecInputBuffers_encoder = encoder.getInputBuffers();
        codecOutputBuffers_encoder = encoder.getOutputBuffers();

        mMuxer.start();

        ByteBuffer buf_after_decode = null;

        // 以下、バッファの処理。インプットバッファの数だけ繰り返す
        for (;;) {
            // TIMEOUT_USが 0 だと待ち時間なしで即結果を返す。
            // 負の値で無限に応答を待つ
            // 正の値だと 値 microseconds分だけ待つ
            int inputBufIndex = codec.dequeueInputBuffer(TIMEOUT_US);

            // Log.d(LOG_TAG, String.format("Input Buffer Index =  %d",
            // inputBufIndex));

            if (inputBufIndex >= 0) {
                // インプットバッファの配列から対象のバッファを取得
                ByteBuffer dstBuf = codecInputBuffers[inputBufIndex];
                // バッファサイズ
                int bufferSize = extractor.readSampleData(dstBuf, 0);
                long presentationTimeUs = 0;
                if (bufferSize < 0) {
                    sawInputEOS = true;
                    bufferSize = 0;
                } else {
                    presentationTimeUs = extractor.getSampleTime();
                }

                // デコード処理してアウトプットバッファに追加？
                codec.queueInputBuffer(inputBufIndex, 0, bufferSize, presentationTimeUs,
                        sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);

                if (!sawInputEOS) {
                    extractor.advance();
                } else {
                    break;
                }
            }

            // 出力処理

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int outputBufIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US);
            // Log.d(LOG_TAG, String.format("Output Buffer Index =  %d",
            // outputBufIndex));
            if (outputBufIndex >= 0) {
                // ここで出力処理をする
                buf_after_decode = codecOutputBuffers[outputBufIndex];

                byte[] buffer_audio = new byte[buf_after_decode.capacity()];
                buf_after_decode.get(buffer_audio, 0, buf_after_decode.limit());
                int buffer_audio_size = buf_after_decode.limit();
                buf_after_decode.position(0);

//                if (!sawInputEOS) {
//                    mMuxer.writeSampleData(auioTrackIndex, buf, info);
//                try {
//                    sleep(1);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//                }


                // チャンクを作る
//                final byte[] chunk = new byte[info.size];
//                buf.get(chunk);
//                buf.clear();
//
//                // AudioTrackに書き込む
//                if (chunk.length > 0) {
//                    mAudioTrack.write(chunk, 0, chunk.length);
//                }
                codec.releaseOutputBuffer(outputBufIndex, false);
            } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                codecOutputBuffers = codec.getOutputBuffers();
            } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // Subsequent data will conform to new format.
                format = codec.getOutputFormat();
            }

            //エンコード
            if(buf_after_decode != null) {
                // TIMEOUT_USが 0 だと待ち時間なしで即結果を返す。
                // 負の値で無限に応答を待つ
                // 正の値だと 値 microseconds分だけ待つ
                int inputBufIndex_encoder = encoder.dequeueInputBuffer(TIMEOUT_US);

                // Log.d(LOG_TAG, String.format("Input Buffer Index =  %d",
                // inputBufIndex));

                if (inputBufIndex_encoder >= 0) {
                    // インプットバッファの配列から対象のバッファを取得
                    ByteBuffer dstBuf = codecInputBuffers_encoder[inputBufIndex];
                    // バッファサイズ
                    int bufferSize = buf_after_decode.limit();
                    long presentationTimeUs = 0;
                    if (bufferSize < 0) {
                        sawInputEOS = true;
                        bufferSize = 0;
                    } else {
                        presentationTimeUs = info.presentationTimeUs;
                    }

                    // デコード処理してアウトプットバッファに追加？
                    encoder.queueInputBuffer(inputBufIndex_encoder, 0, bufferSize, presentationTimeUs,
                            sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);

                    if (!sawInputEOS) {
//                    extractor.advance();
                    } else {
                        break;
                    }
                }

                // 出力処理

                MediaCodec.BufferInfo info_encoder = new MediaCodec.BufferInfo();
                int outputBufIndex_encoder = encoder.dequeueOutputBuffer(info_encoder, TIMEOUT_US);
                // Log.d(LOG_TAG, String.format("Output Buffer Index =  %d",
                // outputBufIndex));
                if (outputBufIndex_encoder >= 0) {
                    // ここで出力処理をする
                    ByteBuffer buf = codecOutputBuffers_encoder[outputBufIndex_encoder];

                    byte[] buffer_audio = new byte[buf.capacity()];
                    buf.get(buffer_audio, 0, buf.limit());
                    int buffer_audio_size = buf.limit();
                    buf.position(0);

                    if (!sawInputEOS) {
                        mMuxer.writeSampleData(auioTrackIndex, buf, info);
//                try {
//                    sleep(1);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
                    }

                    encoder.releaseOutputBuffer(outputBufIndex_encoder, false);
                } else if (outputBufIndex_encoder == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    codecOutputBuffers_encoder = encoder.getOutputBuffers();
                } else if (outputBufIndex_encoder == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // Subsequent data will conform to new format.
                    format_encoder = encoder.getOutputFormat();
                }
            }
        }
        mMuxer.stop();
        mMuxer.release();

        // 終了処理
        extractor.release();
        extractor = null;
        codec.stop();
        codec.release();
        codec = null;
        encoder.stop();
        encoder.release();
        encoder = null;

        //ファイルをアプリから見えるようにする。
        File file = new File(mFilePath);
        String[] split_file = mFilePath.split("/");

        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, split_file[split_file.length-1]);
        values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/3gpp");

        Uri contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        ContentResolver resolver = getApplicationContext().getContentResolver();
        Uri item = resolver.insert(contentUri, values);

        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            inputStream = new FileInputStream(mFilePath);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        try {
            outputStream = resolver.openOutputStream(item);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        byte buf[]=new byte[5120];
        int len;

        try {
            while((len=inputStream.read(buf)) != -1){
                outputStream.write(buf,0,len);
            }
            outputStream.flush();

            outputStream.close();
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //元ファイルを削除する
//        if(file.exists()){
//            file.delete();
//        }

    }


    public void SaveExecute() {         //進捗ダイアログを引数で渡す
        File path;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            path = getApplicationContext().getFilesDir();
        }
        else{
            path = Environment.getExternalStoragePublicDirectory( Environment.DIRECTORY_DCIM);
        }
//        File file = new File(path, getDateTimeString() + ".mp4");
        File dir = new File(path.toString() + "/PhotoGallarySample");
        dir.mkdir();
        File file = new File(dir.toString() + "/" + getDateTimeString() + ".3gp");
        mFilePath = file.toString();

        try {
            mMuxer = new MediaMuxer(file.toString(), MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * get current date and time as String
     * @return
     */
    private static final String getDateTimeString() {
        SimpleDateFormat mDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US);
        final GregorianCalendar now = new GregorianCalendar();
        return mDateTimeFormat.format(now.getTime());
    }

}