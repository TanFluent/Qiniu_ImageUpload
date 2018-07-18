package com.example.tanfulun.test_qiniu;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.example.tanfulun.test_qiniu.utils.Auth;
import com.qiniu.android.http.ResponseInfo;
import com.qiniu.android.storage.UpCompletionHandler;
import com.qiniu.android.storage.UploadManager;

import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static String AccessKey = "IaWG3RLt_co1e5nCvnNjIReVPSL3zANu7nL3-6bu";//此处填你自己的AccessKey
    private static String SecretKey = "Iu5Vs4BMcC2HA3GVz5YmUxpwMYc2sSXX7Q4AvCxb";//此处填你自己的SecretKey
    private static final String TAG = "MainActivity";
    private ImageView avatar;
    private Uri imageUri;
    private static final int REQUEST_CAPTURE = 2;
    private static final int REQUEST_PICTURE = 5;
    private static final int RESULT_CROP = 7;
    private static final int GALLERY_ACTIVITY_CODE = 9;
    private Button fromCarame;
    private Button fromGarllary;
    private Button upload;


    private Uri localUri = null;

    // 重用uploadManager。一般地，只需要创建一个uploadManager对象
    // UploadManager uploadManager = new UploadManager(config);
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        avatar = (ImageView) findViewById(R.id.avatar);
        fromCarame = (Button) findViewById(R.id.carame);
        fromCarame.setOnClickListener(this);
        fromGarllary = (Button) findViewById(R.id.select_img);
        fromGarllary.setOnClickListener(this);
        upload = (Button) findViewById(R.id.upload_img);
        upload.setOnClickListener(this);
        methodRequiresTwoPermission();
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

    private void openCamera() {  //调用相机拍照
        Intent intent = new Intent();
        File file = getOutputMediaFile(); //工具类稍后会给出
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {  //针对Android7.0，需要通过FileProvider封装过的路径，提供给外部调用
            imageUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", file);//通过FileProvider创建一个content类型的Uri，进行封装
        } else { //7.0以下，如果直接拿到相机返回的intent值，拿到的则是拍照的原图大小，很容易发生OOM，所以我们同样将返回的地址，保存到指定路径，返回到Activity时，去指定路径获取，压缩图片

            imageUri = Uri.fromFile(file);

        }
        intent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);//设置Action为拍照
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);//将拍取的照片保存到指定URI
        startActivityForResult(intent, REQUEST_CAPTURE);//启动拍照
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
            Log.e("##tanfulun", "onActivityResult-resultCode: " + requestCode);
            switch (requestCode) {
                case REQUEST_CAPTURE:
                    if (null != imageUri) {
                        localUri = imageUri;
                        performCrop(localUri);
                    }
                    break;
                case REQUEST_PICTURE:
                    localUri = data.getData();
                    performCrop(localUri);
                    break;
                case RESULT_CROP:
                    Bundle extras = data.getExtras();
                    Bitmap selectedBitmap = extras.getParcelable("data");
                    //判断返回值extras是否为空，为空则说明用户截图没有保存就返回了，此时应该用上一张图，
                    //否则就用用户保存的图
                    if (extras == null) {
                        // avatar.setImageBitmap(mBitmap);
                        // storeImage(mBitmap);
                    } else {
                        avatar.setImageBitmap(selectedBitmap);
                        storeImage(selectedBitmap);
                    }
                    break;
                case GALLERY_ACTIVITY_CODE:
                    // 从相册中选择图片返回的intent
                    localUri = data.getData();

                    //String image_abs_path = localUri.getPath();
                    //Log.e("##tanfulun", "GALLERY_ACTIVITY_CODE-image_abs_path: " + image_abs_path);
                    //avatar.setImageURI(localUri);

                    //  setBitmap(localUri);
                    Log.e("##tanfulun", "GALLERY_ACTIVITY_CODE-localUri: " + localUri);

                    performCrop(localUri);

                    break;
            }
        }
    }

    //裁剪图片
    private void performCrop(Uri uri) {
        try {
            Intent intent = new Intent("com.android.camera.action.CROP");
            /*
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                grantUriPermission("com.android.camera", uri,
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            }
            */

            intent.setDataAndType(uri, "image/*");
            // 下面这个crop = true是设置在开启的Intent中设置显示的VIEW可裁剪
            intent.putExtra("crop", "true");
            // aspectX aspectY 是宽高的比例，这里设置的是正方形（长宽比为1:1）
            intent.putExtra("aspectX", 1);
            intent.putExtra("aspectY", 1);
            // outputX outputY 是裁剪图片宽高
            intent.putExtra("outputX", 300);
            intent.putExtra("outputY", 300);
            //裁剪时是否保留图片的比例，这里的比例是1:1
            intent.putExtra("scale", true);
            //是否将数据保留并返回 TODO:这一步会造成代码闪退，具体解释可以google.
            // intent.putExtra("return-data", true);

            // 获得 Uri 格式的图片数据
            Uri imageUri = Uri.fromFile(getOutputMediaFile());

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

    // 这是原版到图片裁剪代码，有bug
    private void performCrop_old(Uri uri) {

        try {
            Intent intent = new Intent("com.android.camera.action.CROP");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                grantUriPermission("com.android.camera", uri,
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            }
            intent.setDataAndType(uri, "image/*");
            intent.putExtra("crop", "true");
            intent.putExtra("aspectX", 1);
            intent.putExtra("aspectY", 1);
            intent.putExtra("outputX", 300);
            intent.putExtra("outputY", 300);
            intent.putExtra("return-data", true);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, getOutputMediaFile().toString());
            startActivityForResult(intent, RESULT_CROP);
        } catch (ActivityNotFoundException anfe) {
            String errorMessage = "你的设备不支持裁剪行为！";
            Toast toast = Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT);
            toast.show();

            Log.e("##tanfulun", "你的设备不支持裁剪行为");
        }
    }

    //建立保存头像的路径及名称
    private File getOutputMediaFile() {
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
        String mImageName = "avatar.png";
        mediaFile = new File(mediaStorageDir.getPath() + File.separator + mImageName);
        return mediaFile;
    }

    //保存图像
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
                break;
            case R.id.carame:
                // 点击"开启相机拍照"按钮
                openCamera();
                break;
            case R.id.upload_img:
                // 点击"上传图片"按钮
                uploadImg2QiNiu();
                break;
        }

    }

    private void uploadImg2QiNiu() {
        UploadManager uploadManager = new UploadManager();
        // 设置图片名字
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        String key = "icon_" + sdf.format(new Date());
        String picPath = getOutputMediaFile().toString();
        Log.i(TAG, "picPath: " + picPath);
        uploadManager.put(picPath, key, Auth.create(AccessKey, SecretKey).uploadToken("image-server"), new UpCompletionHandler() {
            @Override
            public void complete(String key, ResponseInfo info, JSONObject res) {
                // info.error中包含了错误信息，可打印调试
                // 上传成功后将key值上传到自己的服务器
                if (info.isOK()) {
                    Log.i(TAG, "token===" + Auth.create(AccessKey, SecretKey).uploadToken("photo"));
                    String headpicPath = "http://ot6991tvl.bkt.clouddn.com/" + key;
                    Log.i(TAG, "complete: " + headpicPath);
                }
                //上传至阡陌链接
                //     uploadpictoQianMo(headpicPath, picPath);

            }
        }, null);
    }


}

