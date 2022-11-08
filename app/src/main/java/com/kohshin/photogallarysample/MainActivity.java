package com.kohshin.photogallarysample;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextView textView;
    private ImageView imageView;

    VideoView v;

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

        // ID取得
        v = (VideoView)findViewById(R.id.v);

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

        // 動画の指定
        v.setVideoURI(uri);

        // 再生スタート
        v.start();

        // コントローラNO（動画をタップするとメニュー表示）
        v.setMediaController(new MediaController(this));
    }

}