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
import android.widget.TextView;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextView textView;
    private ImageView imageView;

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
        imageView = findViewById(R.id.image_view);

        Button button = findViewById(R.id.button);
        button.setOnClickListener( v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("video/*");

            resultLauncher.launch(intent);
        });
    }

    void openImage(Intent resultData){
        ParcelFileDescriptor pfDescriptor = null;
        try{
            Uri uri = resultData.getData();
            // Uriを表示
            textView.setText( String.format(Locale.US, "Uri:　%s",uri.toString()));

            pfDescriptor = getContentResolver().openFileDescriptor(uri, "r");
            if(pfDescriptor != null){
                FileDescriptor fileDescriptor = pfDescriptor.getFileDescriptor();
                Bitmap bmp = BitmapFactory.decodeFileDescriptor(fileDescriptor);
                pfDescriptor.close();
                imageView.setImageBitmap(bmp);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try{
                if(pfDescriptor != null){
                    pfDescriptor.close();
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

}