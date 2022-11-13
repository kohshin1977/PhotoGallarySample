package com.kohshin.photogallarysample;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
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
import java.util.HashMap;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextView textView;

    private static final String TAG = "MainActivity";

    private static final int DEFAULT_BUFFER_SIZE = 1 * 1024 * 1024;

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

    private static final String DIR_NAME = "DelayCamera";
    private static final SimpleDateFormat mDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US);
    private String DATE_STRING = "";

    void openImage(Intent resultData){
        Uri uri = resultData.getData();
        // Uriを表示
        textView.setText( String.format(Locale.US, "Uri:　%s",uri.toString()));

        File path = getApplicationContext().getFilesDir();

        File dir = new File(path.toString() + "/PhotoGallarySample");
        dir.mkdir();
        File file = new File(dir.toString() + "/" + getDateTimeString() + ".aac");
        String new_file = file.toString();

        try {
            genVideoUsingMuxer(uri, new_file, -1, -1, true, false);
        } catch (IOException e) {
            e.printStackTrace();
        }
//      public void genVideoUsingMuxer(String srcPath, String dstPath, int startMs, int endMs, boolean useAudio, boolean useVideo) throws IOException {

        //ファイルをアプリから見えるようにする。
        File file2 = new File(new_file);
        String[] split_file = new_file.split("/");

        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, split_file[split_file.length-1]);
        values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/*");

        Uri contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        ContentResolver resolver = getApplicationContext().getContentResolver();
        Uri item = resolver.insert(contentUri, values);

        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            inputStream = new FileInputStream(new_file);
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


    @SuppressLint({"NewApi", "WrongConstant"})
    public void genVideoUsingMuxer(Uri uri, String dstPath, int startMs, int endMs, boolean useAudio, boolean useVideo) throws IOException {
        // Set up MediaExtractor to read from the source.
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(getApplicationContext(), uri, null);
        int trackCount = extractor.getTrackCount();
        // Set up MediaMuxer for the destination.
        MediaMuxer muxer;
        muxer = new MediaMuxer(dstPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        // Set up the tracks and retrieve the max buffer size for selected
        // tracks.
        HashMap<Integer, Integer> indexMap = new HashMap<Integer, Integer>(trackCount);
        int bufferSize = -1;
        for (int i = 0; i < trackCount; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            boolean selectCurrentTrack = false;
            if (mime.startsWith("audio/") && useAudio) {
                selectCurrentTrack = true;
            } else if (mime.startsWith("video/") && useVideo) {
                selectCurrentTrack = true;
            }
            if (selectCurrentTrack) {
                extractor.selectTrack(i);
                int dstIndex = muxer.addTrack(format);
                indexMap.put(i, dstIndex);
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    int newSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
                    bufferSize = newSize > bufferSize ? newSize : bufferSize;
                }
            }
        }
        if (bufferSize < 0) {
            bufferSize = DEFAULT_BUFFER_SIZE;
        }
        // Set up the orientation and starting time for extractor.
        MediaMetadataRetriever retrieverSrc = new MediaMetadataRetriever();
        retrieverSrc.setDataSource(getApplicationContext(), uri);
        String degreesString = retrieverSrc.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
        if (degreesString != null) {
            int degrees = Integer.parseInt(degreesString);
            if (degrees >= 0) {
                muxer.setOrientationHint(degrees);
            }
        }
        if (startMs > 0) {
            extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        }
        // Copy the samples from MediaExtractor to MediaMuxer. We will loop
        // for copying each sample and stop when we get to the end of the source
        // file or exceed the end time of the trimming.
        int offset = 0;
        int trackIndex = -1;
        ByteBuffer dstBuf = ByteBuffer.allocate(bufferSize);
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        muxer.start();
        while (true) {
            bufferInfo.offset = offset;
            bufferInfo.size = extractor.readSampleData(dstBuf, offset);
            if (bufferInfo.size < 0) {
                Log.d(TAG, "Saw input EOS.");
                bufferInfo.size = 0;
                break;
            } else {
                bufferInfo.presentationTimeUs = extractor.getSampleTime();
                if (endMs > 0 && bufferInfo.presentationTimeUs > (endMs * 1000)) {
                    Log.d(TAG, "The current sample is over the trim end time.");
                    break;
                } else {
                    bufferInfo.flags = extractor.getSampleFlags();
                    trackIndex = extractor.getSampleTrackIndex();
                    muxer.writeSampleData(indexMap.get(trackIndex), dstBuf, bufferInfo);
                    extractor.advance();
                }
            }
        }
        muxer.stop();
        muxer.release();
        return;
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