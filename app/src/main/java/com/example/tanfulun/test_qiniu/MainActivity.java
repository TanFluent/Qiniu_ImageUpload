package com.example.tanfulun.test_qiniu;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.tanfulun.test_qiniu.utils.Auth;
import com.example.tanfulun.test_qiniu.utils.ParseFilePath;
import com.example.tanfulun.test_qiniu.utils.UnicodeUtils;
import com.qiniu.android.http.ResponseInfo;
import com.qiniu.android.storage.UpCompletionHandler;
import com.qiniu.android.storage.UploadManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import cz.msebera.android.httpclient.util.EncodingUtils;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static String AccessKey = "IaWG3RLt_co1e5nCvnNjIReVPSL3zANu7nL3-6bu";//此处填你自己的AccessKey
    private static String SecretKey = "Iu5Vs4BMcC2HA3GVz5YmUxpwMYc2sSXX7Q4AvCxb";//此处填你自己的SecretKey
    private static String Qiniu_Image_Server_URL = "http://pby8k3kvk.bkt.clouddn.com/";

    private static boolean isHaiGuan = true;

    private static final String TAG = "MainActivity";
    private ImageView avatar_crop;

    private TextView result_classname_tv;
    private TextView result_taxid_tv;
    private TextView result_taxrate_tv;
    private TextView result_recognition_conf_tv;

    private TextView classname_tv;
    private TextView taxid_tv;
    private TextView taxrate_tv;
    private TextView recognition_conf_tv;

    private TextView result_tv;

    private RelativeLayout rr1;
    private RelativeLayout rr2;
    private RelativeLayout rr3;
    private RelativeLayout rr4;


    private static final int REQUEST_CAPTURE = 2;
    private static final int REQUEST_PICTURE = 5;
    private static final int RESULT_CROP = 7;
    private static final int GALLERY_ACTIVITY_CODE = 9;
    private Button fromCarame;
    private Button fromGarllary;

    private Uri cameraImageUri=null; // 拍照获取的图片
    private String cameraImagePath;
    private Uri localUri = null; // 本地相册中，被选中的原始图片；
    private String AlbumImagePath;

    private String results_haiguan_classname = null;
    private String results_taxid = null;
    private String results_taxrate = null;
    private String results_recognition = null;

    private String recognition_results = null;

    private String pic_qiniu_url = null;

    // 用于刷新界面
    private Handler mhandler = new Handler() {
        @Override
        public void handleMessage(android.os.Message msg) {
            switch (msg.what) {
                case 0x001:

                    if(isHaiGuan){
                        result_tv.setVisibility(View.INVISIBLE);
                        setHaiGuanTVVisble();
                        result_classname_tv.setText(results_haiguan_classname);
                        result_taxid_tv.setText(results_taxid);
                        result_taxrate_tv.setText(results_taxrate);
                        result_recognition_conf_tv.setText(results_recognition);
                    }else {
                        result_tv.setText(recognition_results);
                    }

                    Toast.makeText(MainActivity.this, "图片加载完毕", Toast.LENGTH_SHORT).show();
                    break;
                case 0x002:
                    result_tv.setText("图片链接为空");
                    break;
                case 0x003:
                    String pic_path = getOutputMediaPath();
                    Bitmap bmImg = BitmapFactory.decodeFile(pic_path);
                    avatar_crop.setImageBitmap(bmImg);

                    uploadImg2QiNiu();

                    break;
                default:
                    break;
            }
        };
    };

    // 重用uploadManager。一般地，只需要创建一个uploadManager对象
    // UploadManager uploadManager = new UploadManager(config);
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        avatar_crop = (ImageView) findViewById(R.id.avatar_crop);

        result_tv = (TextView)findViewById(R.id.results_tv);

        result_classname_tv = (TextView)findViewById(R.id.results_haiguan_class);
        result_taxid_tv = (TextView)findViewById(R.id.results_tax_id);
        result_taxrate_tv = (TextView)findViewById(R.id.results_tax_rate);
        result_recognition_conf_tv = (TextView)findViewById(R.id.results_recognition);

        classname_tv = (TextView)findViewById(R.id.haiguan_class);
        taxid_tv = (TextView)findViewById(R.id.tax_id);
        taxrate_tv = (TextView)findViewById(R.id.tax_rate);
        recognition_conf_tv = (TextView)findViewById(R.id.recognition);

        rr1 = (RelativeLayout)findViewById(R.id.rr1);
        rr2 = (RelativeLayout)findViewById(R.id.rr2);
        rr3 = (RelativeLayout)findViewById(R.id.rr3);
        rr4 = (RelativeLayout)findViewById(R.id.rr4);

        fromCarame = (Button) findViewById(R.id.carame);
        fromCarame.setOnClickListener(this);
        fromGarllary = (Button) findViewById(R.id.select_img);
        fromGarllary.setOnClickListener(this);
        methodRequiresTwoPermission();

        // get class name list
        //getClassName();

    }

    @AfterPermissionGranted(1)//添加注解，是为了首次执行权限申请后，回调该方法
    private void methodRequiresTwoPermission() {
        String[] perms = {Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        if (EasyPermissions.hasPermissions(this, perms)) {
            //已经申请过权限，直接调用相机
            // openCamera();
        } else {
            EasyPermissions.requestPermissions(this, "需要获取权限",
                    1, perms);
        }
    }

    /**
     * 调用相机拍照
     *
     * */
    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";

        // getExternalFilesDir() = /storage/emulated/0/Android/data/app_id/files/Pictures
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        cameraImagePath = image.getAbsolutePath();
        return image;
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File
                Log.i("##tanfulun", "dispatchTakePictureIntent: createImageFile failed");
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                cameraImageUri = FileProvider.getUriForFile(this,
                        BuildConfig.APPLICATION_ID + ".provider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
                startActivityForResult(takePictureIntent, REQUEST_CAPTURE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // Forward results to EasyPermissions
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            Log.e("##tanfulun", "onActivityResult-requestCode: " + requestCode);
            switch (requestCode) {
                case REQUEST_CAPTURE:

                    if (null != cameraImageUri) {
                        Log.i("##tanfulun", "onActivityResult: cameraImageUri.path = "+cameraImageUri.getPath());

                        performCrop(cameraImageUri);
                    }else {
                        Log.i("##tanfulun", "onActivityResult: cameraImageUri == null");
                    }

                    break;

                case REQUEST_PICTURE:
                    // 目前废弃
                    localUri = data.getData();
                    performCrop(localUri);
                    break;

                case RESULT_CROP:
                    Bundle extras = data.getExtras();
                    //判断返回值extras是否为空，
                    // 为空则说明用户截图没有保存就返回了；理论上，android不希望通过intent传递裁剪的图片，所以此时返回必定为空；
                    // 否则就用用户保存的图,图片保存位置通过调用"getOutputMediaFile()"函数获取；
                    if (extras == null) {
                        Log.i("##tanfulun", "onActivityResult: RESULT_CROP--extras == null");
                    } else {
                        Log.i("##tanfulun", "onActivityResult: RESULT_CROP--extras != null");
                    }
                    // get cropped image and show on imageView
                    File pictureFile = getOutputMediaFile();
                    if (pictureFile == null) {
                        Log.d("##tanfulun",
                                "Error creating media file, check storage permissions: ");
                        return;
                    }
                    mhandler.sendEmptyMessage(0x003);
                    break;

                case GALLERY_ACTIVITY_CODE:
                    // 从相册中选择图片返回的intent

                    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT){
                        AlbumImagePath = ParseFilePath.getPath(getApplicationContext(), data.getData());
                        Log.i("##tanfulun", "GALLERY_ACTIVITY_CODE-AlbumImagePath: " + AlbumImagePath);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
                            localUri = FileProvider.getUriForFile(this,
                                    getApplicationContext().getPackageName() + ".provider", new File(AlbumImagePath));
                        }else{
                            localUri = Uri.fromFile(new File(AlbumImagePath));
                        }

                    }else {
                        localUri = data.getData();
                    }

                    Log.i("##tanfulun", "GALLERY_ACTIVITY_CODE-localUri.path: " + localUri.getPath());

                    Log.e("##tanfulun", "GALLERY_ACTIVITY_CODE-localUri: " + localUri);

                    performCrop(localUri);

                    break;
            }
        }
    }

    /**
     * 裁剪图片
     */
    private void performCrop(Uri uri) {
        try {
            // del old image
            delCropPic();

            // start crop image
            Intent intent = new Intent("com.android.camera.action.CROP");

            // 获取读取 uri 的权限，在 Android 7.0 及以上版本必须注意
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                grantUriPermission("com.example.tanfulun.test_qiniu", uri,
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);

                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            }

            intent.setDataAndType(uri, "image/*");
            // 下面这个crop = true是设置在开启的Intent中设置显示的VIEW可裁剪
            intent.putExtra("crop", "true");

            // aspectX aspectY 是宽高的比例，这里设置的是正方形（长宽比为1:1
            // 不设置表示按任意比例获取
            //intent.putExtra("aspectX", 0.1);
            //intent.putExtra("aspectY", 0.1);

            // outputX outputY 是裁剪图片宽高
            intent.putExtra("outputX", 600);
            intent.putExtra("outputY", 600);

            //裁剪时是否保留图片的比例，这里的比例是1:1
            intent.putExtra("scale", true);

            //是否将数据保留并返回 TODO:这一步会造成代码闪退，具体解释可以google.
            intent.putExtra("return-data", false);

            // 获得 Uri 格式的图片数据
            Uri imageUri = Uri.fromFile(getOutputMediaFile());

            intent.putExtra("outputFormat", Bitmap.CompressFormat.JPEG.toString());

            //intent.putExtra(MediaStore.EXTRA_OUTPUT, getOutputMediaFile().toString());

            // 图片存储到手机
            intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);

            Log.i("##tanfulun", "图片裁剪存储成功！");

            startActivityForResult(intent, RESULT_CROP);
        } catch (ActivityNotFoundException anfe) {
            String errorMessage = "你的设备不支持裁剪行为！";
            Toast toast = Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT);
            toast.show();

            Log.e("##tanfulun", "你的设备不支持裁剪行为");
        }
    }

    /**
     * 建立保存图像的路径及名称,并建立一个图像文件实例.
     * */
    private File getOutputMediaFile() {

        String mImageName = "avatar.png"; // names of local cropped image

        // Environment.getExternalStorageDirectory() = /storage/emulated/0/
        File mediaStorageDir = new File(Environment.getExternalStorageDirectory()
                + "/Android/data/"
                + getApplicationContext().getPackageName()
                + "/Files");
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return null;
            }
        }

        File mediaFile;
        mediaFile = new File(mediaStorageDir.getPath() + File.separator + mImageName);
        return mediaFile;
    }

    private String getOutputMediaPath() {

        String mImageName = "avatar.png"; // names of local cropped image

        String picPath = Environment.getExternalStorageDirectory()
                + "/Android/data/"
                + getApplicationContext().getPackageName()
                + "/Files"
                + "/"
                + mImageName;

        return picPath;
    }

    /**
     * 删除剪切的图像
     * */
    private int delCropPic(){
        String mImageName = "avatar.png"; // names of local cropped image

        File mediaStorageDir = new File(Environment.getExternalStorageDirectory()
                + "/Android/data/"
                + getApplicationContext().getPackageName()
                + "/Files");

        // del "avatar.png" if it is already exist
        File old_image_file = new File(mediaStorageDir.getPath() + File.separator + mImageName);
        if (old_image_file.exists()) {
            Log.i("##tanfulun", mImageName+" is already exist!");
            //删除系统缩略图
            getContentResolver().delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    MediaStore.Images.Media.DATA + "=?", new String[]{old_image_file.getPath()});
            old_image_file.delete();
            Log.i("##tanfulun", mImageName+"and its thumbnail image are del!");
        }else {
            return 0;
        }

        return 1;
    }

    /**
     * 保存图像
     */
    private void storeImage(Bitmap image) {
        File pictureFile = getOutputMediaFile();
        if (pictureFile == null) {
            Log.d(TAG,
                    "Error creating media file, check storage permissions: ");// e.getMessage());
            return;
        }
        try {
            FileOutputStream fos = new FileOutputStream(pictureFile);
            image.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();
        } catch (FileNotFoundException e) {
            Log.d(TAG, "File not found: " + e.getMessage());
        } catch (IOException e) {
            Log.d(TAG, "Error accessing file: " + e.getMessage());
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.select_img:
                // 点击的是"从相册选图片"按钮
                Intent gallery_Intent = new Intent(Intent.ACTION_PICK, null);
                //gallery_Intent.setType("image/*");  // "image/*"表示所有类型的图片
                gallery_Intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
                startActivityForResult(gallery_Intent, GALLERY_ACTIVITY_CODE);

                // 清空recognition_results
                result_tv.setText("图片上传中...");
                result_tv.setVisibility(View.VISIBLE);
                setHaiGuanTVInvisble();

                break;

            case R.id.carame:
                // 点击"开启相机拍照"按钮
                dispatchTakePictureIntent();

                // 清空recognition_results
                result_tv.setText("图片上传中...");
                result_tv.setVisibility(View.VISIBLE);
                setHaiGuanTVInvisble();

                break;
        }

    }

    /**
     * 上传图片到qiniu服务器
     * */
    private void uploadImg2QiNiu() {
        UploadManager uploadManager = new UploadManager();
        // 设置图片名字
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        String key = "icon_" + sdf.format(new Date());
        String picPath = getOutputMediaFile().toString();
        Log.i(TAG, "picPath: " + picPath);
        String token = Auth.create(AccessKey, SecretKey).uploadToken("image-server");
        uploadManager.put(picPath, key, token, new UpCompletionHandler() {
            @Override
            public void complete(String key, ResponseInfo info, JSONObject res) {
                // info.error中包含了错误信息，可打印调试
                // 上传成功后将key值上传到自己的服务器
                String headpicPath = null;
                if (info.isOK()) {
                    Log.i(TAG, "token===" + Auth.create(AccessKey, SecretKey).uploadToken("photo"));
                    headpicPath = Qiniu_Image_Server_URL + key;
                    Log.i(TAG, "complete: image_url" + headpicPath);
                }
                // 调用图片识别服务
                if(headpicPath == null){
                    Log.i("##tanfulun", "image uri from qiniu is null");
                    new Thread() {
                        public void run() {
                            mhandler.sendEmptyMessage(0x002);
                        };
                    }.start();
                }else {
                    Log.i("##tanfulun", "call image_recognition()");
                    pic_qiniu_url = headpicPath;
                    image_recognition();
                }

            }
        }, null);
    }

    /**
     * 识别url指向到图片
     * */
    private void image_recognition(){
        // 测试接口以及方法
        // curl -X POST http://47.93.252.203:8080/hello

        // 识别接口以及方法
        // curl -H "Content-Type:application/json" -X POST --data '{"image_path":"xxxxxx"}' http://47.93.252.203:8080/cnn_cls/vispred
        new Thread() {
            public void run() {
                try {
                    Log.i("##tanfulun", "image_recognition run(): http post start");
                    LoginByPost();
                } catch (Exception e) {
                    Log.i("##tanfulun", "image_recognition run(): http post fail");
                    e.printStackTrace();
                }
                mhandler.sendEmptyMessage(0x001);
            };
        }.start();
        Log.i("##tanfulun", "image recognition done");
    }

    /**
     * 向图片识别服务器发出POST请求
     * */
    private void LoginByPost() {
        String LOGIN_URL = "http://47.93.252.203:8080/cnn_cls/vispred";

        if(isHaiGuan){
            LOGIN_URL = "http://47.93.252.203:8080/cnn_cls/vispred_zf";
        }

        String TEST_URL = "http://47.93.252.203:8080/hello";
        String msg = "";
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(LOGIN_URL).openConnection();
            // 设置请求方式,请求超时信息
            conn.setRequestMethod("POST");
            //conn.setReadTimeout(5000);
            conn.setConnectTimeout(5000);
            // 设置运行输入,输出:
            conn.setDoOutput(true);
            //conn.setDoInput(true);
            // Post方式不能缓存,需手动设置为false
            conn.setUseCaches(false);
            // 我们请求的数据:
            String data = "{\"image_path\":\""+pic_qiniu_url+"\"}";
            Log.i("##tanfulun", "data: "+ data);
            // 这里可以写一些请求头的东东...
            conn.setRequestProperty("Content-Type","application/json");

            // 获取输出流
            OutputStream out = conn.getOutputStream();
            out.write(data.getBytes());
            out.flush();
            if (conn.getResponseCode() == 200) {
                Log.i("##tanfulun", "LoginByPost: Post success");
                // 获取响应的输入流对象
                InputStream is = conn.getInputStream();
                // 创建字节输出流对象
                ByteArrayOutputStream message = new ByteArrayOutputStream();
                // 定义读取的长度
                int len = 0;
                // 定义缓冲区
                byte buffer[] = new byte[1024];
                // 按照缓冲区的大小，循环读取
                while ((len = is.read(buffer)) != -1) {
                    // 根据读取的长度写入到os对象中
                    message.write(buffer, 0, len);
                }
                // 释放资源
                is.close();
                message.close();
                // 返回字符串
                msg = new String(message.toByteArray());

                //recognition_results = parseResponse(msg);
                if(isHaiGuan){
                    //recognition_results = parseResponse_haiguan(msg);
                    parseResponse_haiguan(msg);
                }else {
                    recognition_results = parseResponse(msg);
                }

            }
            else {
                Log.i("##tanfulun", "LoginByPost: Post failed");
            }
        } catch (Exception e) {
            recognition_results = "http post Exception";
            e.printStackTrace();
        }
    }

    private String parseResponse(String msg){
        String[] parsed_msg = null;
        String parsed_response = "";

        ArrayList<String> arr_className = new ArrayList<>();
        ArrayList<Float> arr_conf = new ArrayList<>();

        String msg1 = msg.replace("[","");
        String msg2 = msg1.replace("]","");
        String msg3 = msg2.replace(":",",");
        String msg4 = msg3.replace("{","");
        String msg5 = msg4.replace("}","");
        String msg6 = msg5.replace("\"","");

        parsed_msg = msg6.trim().split("\\s*,\\s*");

        int cnt = 0;
        String conf = null;

        if("ok".equals(parsed_msg[1])){
            for(String item : parsed_msg){
                if(cnt>2){
                    if(cnt%2==0){
                        arr_className.add(UnicodeUtils.unicodeToString(item));
                        /*
                        parsed_response = parsed_response + UnicodeUtils.unicodeToString(item)
                                +"("
                                + conf
                                +")\n";
                        */
                    }else{
                        // 保留到小数点后两位
                        conf = item.substring(0,4);
                        // string to float
                        Float f_conf = Float.parseFloat(conf);

                        arr_conf.add(f_conf);
                    }

                }
                cnt = cnt + 1;
            }
        }else{
            Log.i("##tanfulun", "parseResponse:  status is not ok");
            return "超出识别范围,请重新上传";
        }

        // sum conf values
        Float sum_conf = new Float(0.0);
        for(Float val : arr_conf){
            sum_conf = sum_conf + val;
        }
        // norm conf values
        for(int i=0, n=arr_conf.size(); i < n; i++){
            Float norm_conf = arr_conf.get(i) / sum_conf;
            String s_norm_conf = Float.toString(norm_conf).substring(0,4);


            parsed_response = parsed_response + arr_className.get(i)
                    +"("
                    + s_norm_conf
                    +")\n";
        }


        return parsed_response;
    }

    private String parseResponse_haiguan(String msg){
        String[] parsed_msg = null;
        String parsed_classname_conf = "";
        String parsed_taxid = "";
        String parsed_taxrate = "";
        String parsed_classname_haiguan = "";

        ArrayList<String> arr_className = new ArrayList<>();
        ArrayList<Float> arr_conf = new ArrayList<>();
        ArrayList<String> arr_TaxID = new ArrayList<>();
        ArrayList<String> arr_className_haiguan = new ArrayList<>();
        ArrayList<String> arr_TaxRate = new ArrayList<>();

        String msg1 = msg.replace("[","");
        String msg2 = msg1.replace("]","");
        String msg3 = msg2.replace(":",",");
        String msg4 = msg3.replace("{","");
        String msg5 = msg4.replace("}","");
        String msg6 = msg5.replace("\"","");

        parsed_msg = msg6.trim().split("\\s*,\\s*");

        int cnt = 0;
        int idx = 0;
        String conf = null;

        Log.i("##tanfulun", "parseResponse_haiguan: parsed_msg--" + msg6);

        if("ok".equals(parsed_msg[1])){
            for(String item : parsed_msg){
                if(cnt>2){

                    if(idx == 0){
                        arr_className.add(UnicodeUtils.unicodeToString(item));
                        idx = idx + 1;
                    }else if (idx == 1){
                        arr_TaxID.add(item);
                        idx = idx + 1;
                    }else if (idx == 2){
                        try{
                            arr_className_haiguan.add(UnicodeUtils.unicodeToString(item));
                        }catch (Exception e){
                            Log.i("##tanfulun", "arr_className_haiguan: can't parse!");
                            arr_className_haiguan.add(item);
                        }
                        idx = idx + 1;
                    }else if (idx == 3){
                        //String tmp = item.replace("%","%%");
                        arr_TaxRate.add(item);
                        idx = idx + 1;
                    }else {
                        // 保留到小数点后两位
                        String tmp_conf;
                        if(item.length()>4){
                            tmp_conf = item.substring(0,4);
                        }else{
                            tmp_conf = item;
                        }
                        conf = tmp_conf;
                        Log.i("##tanfulun", "conf: " + conf + " item:"+item);
                        // string to float
                        Float f_conf = Float.parseFloat(conf);

                        arr_conf.add(f_conf);

                        if (idx == 4){
                            idx = 0;
                        }else {
                            return "Invalid msg!";
                        }
                    }

                }
                cnt = cnt + 1;
            }
        }else{
            Log.i("##tanfulun", "parseResponse:  status is not ok");
            return "超出识别范围,请重新上传";
        }

        // validate response
        if(arr_conf.size()==0){
            return "超出识别范围,请重新上传";
        }

        // sum conf values
        Float sum_conf = new Float(0.0);
        for(Float val : arr_conf){
            sum_conf = sum_conf + val;
        }
        // norm conf values
        for(int i=0, n=arr_conf.size(); i < n; i++){
            Float norm_conf = arr_conf.get(i) / sum_conf;
            String s_norm_conf = Float.toString(norm_conf);

            String tmp_conf = s_norm_conf;

            if(s_norm_conf.length()>4){
                tmp_conf = s_norm_conf.substring(0,4);
            }

            parsed_classname_haiguan = parsed_classname_haiguan
                    + arr_className_haiguan.get(i)
                    + "\n\n";

            parsed_classname_conf = parsed_classname_conf
                    + arr_className.get(i)
                    + "("
                    + tmp_conf
                    + ")"
                    + "\n\n";

            parsed_taxid = parsed_taxid
                    + arr_TaxID.get(i)
                    + "\n\n";

            parsed_taxrate = parsed_taxrate
                    + arr_TaxRate.get(i)
                    + "\n\n";
        }

        results_haiguan_classname = parsed_classname_haiguan;
        results_recognition = parsed_classname_conf;
        results_taxid = parsed_taxid;
        results_taxrate = parsed_taxrate;

        //Log.i("##tanfulun", "parseResponse_haiguan: parsed_response--" + parsed_response);
        return "";
    }

    private void getClassName(){

        String res="";
        try{
            //得到资源中的asset数据流
            InputStream in = getResources().getAssets().open("6880.txt");

            int length = in.available();
            byte [] buffer = new byte[length];

            in.read(buffer);
            in.close();
            res = EncodingUtils.getString(buffer, "UTF-8");
            String[] classNames = res.split("\n");

            //Log.i("##tanfulun", "getClassName: res " + res);
            //Log.i("##tanfulun", "getClassName: res[0] " + classNames[0]);

        }catch(Exception e){

            Log.i("##tanfulun", "readTxtFromAssets: failed");
            e.printStackTrace();

        }
    }

    private void setHaiGuanTVVisble(){
        result_classname_tv.setVisibility(View.VISIBLE);
        result_taxid_tv.setVisibility(View.VISIBLE);
        result_taxrate_tv.setVisibility(View.VISIBLE);
        result_recognition_conf_tv.setVisibility(View.VISIBLE);

        classname_tv.setVisibility(View.VISIBLE);
        taxid_tv.setVisibility(View.VISIBLE);
        taxrate_tv.setVisibility(View.VISIBLE);
        recognition_conf_tv.setVisibility(View.VISIBLE);

        rr1.setVisibility(View.VISIBLE);
        rr2.setVisibility(View.VISIBLE);
        rr3.setVisibility(View.VISIBLE);
        rr4.setVisibility(View.VISIBLE);
    }

    private void setHaiGuanTVInvisble(){
        result_classname_tv.setVisibility(View.INVISIBLE);
        result_taxid_tv.setVisibility(View.INVISIBLE);
        result_taxrate_tv.setVisibility(View.INVISIBLE);
        result_recognition_conf_tv.setVisibility(View.INVISIBLE);

        classname_tv.setVisibility(View.INVISIBLE);
        taxid_tv.setVisibility(View.INVISIBLE);
        taxrate_tv.setVisibility(View.INVISIBLE);
        recognition_conf_tv.setVisibility(View.INVISIBLE);

        rr1.setVisibility(View.INVISIBLE);
        rr2.setVisibility(View.INVISIBLE);
        rr3.setVisibility(View.INVISIBLE);
        rr4.setVisibility(View.INVISIBLE);
    }
}

