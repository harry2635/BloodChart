package com.example.bloodchart;

import static androidx.constraintlayout.widget.ConstraintLayoutStates.TAG;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageCapture;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Rational;
import android.util.Size;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.theartofdev.edmodo.cropper.CropImage;
import com.theartofdev.edmodo.cropper.CropImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Permissions;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class HomeActivity extends AppCompatActivity{
    private ImageView imageView;
    private Button btn_signup, btn_data, btn_OCR, btn_csv;
    private TextView tv_username,tv_ocrResult;
    private  static final int REQUEST_CAMERA_CODE = 100;
    Bitmap bitmap;
    private PermissionManager permissionManager;
    private String[] permissions = {Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE};
    private ImageCapture imgCap;

    private Integer count;
    public String account, username;
    public ArrayList<String> userRecordData = new ArrayList<String>();
    DBHelper DB;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        //Permission
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1000);
        }

        if(ContextCompat.checkSelfPermission(HomeActivity.this, Manifest.permission.CAMERA)
           != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(HomeActivity.this, new String[]{
                    Manifest.permission.CAMERA
            }, REQUEST_CAMERA_CODE);
        }

//        permissionManager = PermissionManager.getInstance(this);




        //Button
        btn_data = findViewById(R.id.btn_data);
        btn_signup = findViewById(R.id.btn_signup);
        btn_OCR = findViewById(R.id.btn_OCR);
        btn_csv = findViewById(R.id.btn_csv);

        //TextView
        tv_username = findViewById(R.id.tv_name);
        tv_ocrResult = findViewById(R.id.tv_ocrResult);

        //Bundle
        Bundle bundle = getIntent().getExtras();
        username = bundle.getString("username");
        account = bundle.getString("account");

        Bundle bundle2datalist = new Bundle();


        tv_username.setText(username);

        DB = new DBHelper(this);

        //Click
        btn_signup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplicationContext(), LoginActivity.class);
                startActivity(intent);
            }
        });

        btn_csv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                StringBuffer csvText = DB.searchUserRecordData(account);
                makeCSV(csvText, account);
            }
        });

        btn_data.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Intent intent = new Intent(getApplicationContext(), DatalistActivity.class);
                bundle2datalist.putString("username", username);
                bundle2datalist.putString("account", account);
                intent.putExtras(bundle2datalist);
                startActivity(intent);

            }
        });

        btn_OCR.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                if(!permissionManager.checkPermissions(permissions)){
//                    permissionManager.askPermissions(HomeActivity.this,
//                            permissions,100);
//                }else {
//                    openCamera();
//                }
                CropImage.activity().setGuidelines(CropImageView.Guidelines.ON).start(HomeActivity.this);

            }
        });


    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @NonNull Intent data){
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE){
            CropImage.ActivityResult result = CropImage.getActivityResult(data);
            if(resultCode == RESULT_OK){
                Uri resultUri = result.getUri();
                try {
                    bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), resultUri);
                    getTextFromImage(bitmap);
                }catch (IOException e){
                    e.printStackTrace();
                }


            }
        }
    }
    private void getTextFromImage(Bitmap bitmap){
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        Task<Text> result =
                recognizer.process(image)
                        .addOnSuccessListener(new OnSuccessListener<Text>() {
                            @Override
                            public void onSuccess(Text visionText) {
                                System.out.println(visionText.getTextBlocks().size());
                                if(visionText.getTextBlocks().size() % 4 == 0){
                                    String[] title = {"date", "time", "sbp", "dbp"};
                                    count = 0;
                                    ArrayList<String> ocrData = new ArrayList<>();

                                    Integer blockLength = visionText.getTextBlocks().size() / 4 ;

                                    for(Text.TextBlock block : visionText.getTextBlocks()){
                                        String blockText = block.getText();
                                        ocrData.add(blockText);
//                                        System.out.println("CC");
//                                        System.out.println(blockText);
                                    }
                                    for(int i = 0; i<blockLength; i++){
                                        Boolean insert = DB.insertBPdata(account,
                                                ocrData.get(i*4),ocrData.get(i*4+1),ocrData.get(i*4+2),ocrData.get(i*4+3));
//                                        System.out.println(ocrData.get(i*4));
//                                        System.out.println(ocrData.get(i*4 + 1));
//                                        System.out.println(ocrData.get(i*4 + 2));
//                                        System.out.println(ocrData.get(i*4 + 3));
                                    }

                                    System.out.println("Total");
                                    System.out.println(ocrData);

                                    Toast.makeText(HomeActivity.this,"OCR Success", Toast.LENGTH_SHORT).show();
                                }else{
                                    Toast.makeText(HomeActivity.this,"字體無法識別", Toast.LENGTH_SHORT).show();
                                }

                            }
                        })
                        .addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                System.out.println("---------------OCR Failed----------------");

                            }
                        });

//        System.out.println("---------------OCR outside----------------");
//        for(Text.TextBlock block : result.getResult().getTextBlocks()){
//            String blockText = block.getText();
//            System.out.println("CC");
//        }

    }


//    @Override public void onRequestPermissionResult(int requestCode,
//                                                    @NonNull String[] permissions,
//                                                    @NonNull int[] grantResults){
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        if(requestCode == 100){
//            permissionManager.handlePermissionResult(HomeActivity.this,
//                    100, permissions,
//                    grantResults);
//
//            openCamera();
//        }
//    }
//
//    public void openCamera(){
//        CameraX.unbindAll();
//        Rational aspectRatio = new Rational(textureView.getWidth(), textureView.getHeight());
//        Size screen = new Size()
//    }


    public void creatCSV(StringBuffer userbpdata){
        new Thread(()->{
            String fileName = username + ".csv";
            StringBuffer csvText = userbpdata;


            Log.d(TAG, "makeCSV: \n"+csvText);//可在此監視輸出的內容
            runOnUiThread(()->{
                try{
                    StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
                    StrictMode.setVmPolicy(builder.build());
                    builder.detectFileUriExposure();

                    FileOutputStream out = openFileOutput(fileName, Context.MODE_PRIVATE);
                    out.write((csvText.toString().getBytes()));
                    out.close();
                    File fileLocation = new File(Environment.
                            getExternalStorageDirectory().getAbsolutePath(), fileName);
                    FileOutputStream fos = new FileOutputStream(fileLocation);
                    fos.write(csvText.toString().getBytes());
                    Uri path = Uri.fromFile(fileLocation);
                    Intent fileIntent = new Intent(Intent.ACTION_SEND);
                    fileIntent.setType("text/csv");
                    fileIntent.putExtra(Intent.EXTRA_SUBJECT, fileName);
                    fileIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    fileIntent.putExtra(Intent.EXTRA_STREAM, path);
                    startActivity(Intent.createChooser(fileIntent, "outputfile"));
                }catch (IOException e){
                    e.printStackTrace();
                    Log.w(TAG,"makeCSV" + e.toString());

                }
            });
        }).start();
    }

    private void makeCSV(StringBuffer userbpdata, String account) {
        new Thread(() -> {
            /**決定檔案名稱*/
            String date = new SimpleDateFormat("yyyy-MM-dd",
                    Locale.getDefault()).format(System.currentTimeMillis());
            String fileName = "[" + date + "]" + account + ".csv";
            /**撰寫內容*/
            //以下用詞：直行橫列
            //設置第一列的內容
            String[] title ={"Id","Chinese","English","Math","Physical"};
            StringBuffer csvText = userbpdata;

            Log.d(TAG, "makeCSV: \n"+csvText);//可在此監視輸出的內容
            runOnUiThread(() -> {
                try {
                    //->遇上exposed beyond app through ClipData.Item.getUri() 錯誤時在onCreate加上這行
                    StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
                    StrictMode.setVmPolicy(builder.build());
                    builder.detectFileUriExposure();
                    //->遇上exposed beyond app through ClipData.Item.getUri() 錯誤時在onCreate加上這行
                    FileOutputStream out = openFileOutput(fileName, Context.MODE_PRIVATE);
                    out.write((csvText.toString().getBytes()));
                    out.close();
                    File fileLocation = new File(Environment.
                            getExternalStorageDirectory().getAbsolutePath(), fileName);
                    FileOutputStream fos = new FileOutputStream(fileLocation);
                    fos.write(csvText.toString().getBytes());
                    Uri path = Uri.fromFile(fileLocation);
                    Intent fileIntent = new Intent(Intent.ACTION_SEND);
                    fileIntent.setType("text/csv");
                    fileIntent.putExtra(Intent.EXTRA_SUBJECT, fileName);
                    fileIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    fileIntent.putExtra(Intent.EXTRA_STREAM, path);
                    startActivity(Intent.createChooser(fileIntent, "輸出檔案"));
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.w(TAG, "makeCSV: "+e.toString());
                }
            });
        }).start();
    }//makeCSV






}