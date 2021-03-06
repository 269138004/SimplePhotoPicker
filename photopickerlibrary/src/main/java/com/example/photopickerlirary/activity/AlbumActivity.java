package com.example.photopickerlirary.activity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.SharedElementCallback;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;


import com.example.photopickerlirary.AlbumAsync;
import com.example.photopickerlirary.AlbumHelper;
import com.example.photopickerlirary.BaseActivity;
import com.example.photopickerlirary.Configs;
import com.example.photopickerlirary.R;
import com.example.photopickerlirary.interfaces.PhotoDetailSelect;
import com.example.photopickerlirary.interfaces.SelectPhotoResult;
import com.example.photopickerlirary.adapter.AlbumAdapter;
import com.example.photopickerlirary.entity.PhotoBean;
import com.example.photopickerlirary.entity.PhotoParentBean;
import com.example.photopickerlirary.utils.CommonUtils;
import com.example.photopickerlirary.utils.ShareElementUtils;
import com.example.photopickerlirary.utils.StatusBarUtil;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
public class AlbumActivity extends BaseActivity implements AlbumAsync.PhotoLoadListener, AlbumAdapter.PhotoClickListener, BaseActivity.PermissionRequestListener, View.OnClickListener, PhotoDetailSelect {

    private static final String TAG = "AlbumActivity";

    private int selectImg , unSelectImg , camearImg , titleView;

    private List<PhotoParentBean> parentNameList = new ArrayList<>();//所有的父文件夹
    private List<PhotoBean> photoPath = new ArrayList<>();//所有图片的文件夹
    private List<PhotoBean> currentPickPhotoList = new ArrayList<>();//当前选中图片集合
    private AlbumAdapter albumAdapter;
    private int pickPhotoNums = 1;
    private boolean isInDetailpage = true;
    private File tempFile;

    private static SelectPhotoResult selectPhotoResult;
    private ArrayList<String> listExtra;
    private LinearLayout album_container , album_old_title;
    private RecyclerView recycler;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_album);
        StatusBarUtil.setLightMode(this);
        StatusBarUtil.setColorNoTranslucent(this, getResources().getColor(R.color.transprent));
        initIntent();
        initView();
        DetailActivity.setOnPhotoDetailSelected(this);
        //保证当退出详情页面后返回到相应的图片位置
        ActivityCompat.setExitSharedElementCallback(this, new SharedElementCallback() {
            @Override
            public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {
                super.onMapSharedElements(names, sharedElements);
                sharedElements.put(getResources().getString(R.string.share_photo_detail) , getViewByPsn(DetailActivity.currentPosition));
            }
        });
    }

    private void initIntent() {
        Intent intent = getIntent();
        if (intent == null)return;
        pickPhotoNums = intent.getIntExtra(Configs.PICK_PHOTO_NUMS_FLAG , 1);
        listExtra = intent.getStringArrayListExtra(Configs.ALREADY_PICK_PHOTO_LIST);
        selectImg = intent.getIntExtra(Configs.ADAPTER_SELECT_IMG , 0);
        unSelectImg = intent.getIntExtra(Configs.ADAPTER_UNSELECT_IMG , 0);
        camearImg = intent.getIntExtra(Configs.ADAPTER_CAMEAR_IMG , 0);
        titleView = intent.getIntExtra(Configs.ALBUM_TITLE_VIEW , 0);
        isInDetailpage = intent.getBooleanExtra(Configs.ALBUM_IS_IN_DETAIL_PAGE , true);
        if (listExtra == null)return;
        for (int i = 0; i < listExtra.size() ; i ++){
            PhotoBean bean = new PhotoBean();
            bean.setSelected(true);
            bean.setPath(listExtra.get(i));
            currentPickPhotoList.add(bean);
        }
    }

    private void initView() {
        recycler = findViewById(R.id.recycler);
        GridLayoutManager gridLayoutManager = new GridLayoutManager(this , 4);
        recycler.setLayoutManager(gridLayoutManager);
        albumAdapter = new AlbumAdapter(photoPath , this , selectImg , unSelectImg , camearImg);
        albumAdapter.setOnPhotoClickListener(this);
        recycler.setAdapter(albumAdapter);
        requestReadPermission();
        if (titleView != 0){
            album_container = findViewById(R.id.album_container);
            album_old_title = findViewById(R.id.album_old_title);
            album_container.removeView(album_old_title);
            View newView = LayoutInflater.from(this).inflate(titleView , album_container , false);
            album_container.addView(newView , 0);
        }
        View album_commit = findViewById(R.id.album_title_finish);
        View album_name = findViewById(R.id.album_title_name);
        View album_back = findViewById(R.id.album_title_back);
        if (album_back != null){
            album_back.setOnClickListener(this);
        }else {
            Toast.makeText(this, "you don't set back button and don't set back id", Toast.LENGTH_LONG).show();
        }
        if (album_commit != null){
            album_commit.setOnClickListener(this);
        }else {
            Toast.makeText(this, "you don't set finish button and don't set finish id", Toast.LENGTH_LONG).show();
        }

    }

    @Override
    public void onPhotoFinished(Map<String, List<PhotoBean>> resultMap) {
        refreshUI(resultMap);
    }

    /**
     * 更新界面
     * @param map
     */
    private void refreshUI(Map<String , List<PhotoBean>> map){
        if (map == null){
            Log.e(TAG, "refreshUI: 没有查询出来图片");
            return;
        }
        Iterator<Map.Entry<String, List<PhotoBean>>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()){
            Map.Entry<String, List<PhotoBean>> next = iterator.next();
            String key = next.getKey();
            List<PhotoBean> value = next.getValue();
            for (int i = 0 ; i < value.size() ; i ++){
                PhotoBean photoBean = value.get(i);
                for (int j = 0 ; j < currentPickPhotoList.size() ; j ++){
                    PhotoBean photoBean1 = currentPickPhotoList.get(j);
                    if (photoBean.getPath().equals(photoBean1.getPath())){
                        photoBean.setSelected(true);
                    }
                }
            }
            //获得所有父文件夹的名字和包含图片的数量
            PhotoParentBean photoParentBean = new PhotoParentBean();
            photoParentBean.setName(key);
            photoParentBean.setCount(String.valueOf(value.size()));
            photoParentBean.setChilds(value);
            parentNameList.add(photoParentBean);
            //获得所有的图片
            if (key.equals("photo_picker")){
                Collections.reverse(value);
                photoPath.addAll(1 , value);
            }else {
                photoPath.addAll(value);
            }
        }
        albumAdapter.notifyDataSetChanged();
    }

    /**
     * 选中图片
     */
    @Override
    public void onPhotoSelectClick(int psn) {
        selectPhoto(psn);
    }

    /**
     * 查看图片详情
     */
    @Override
    public void onPhotoClick(View view , String photoUrl , int psn) {
        if (isInDetailpage){
            List<PhotoBean> resultBean = new ArrayList<>();
            resultBean.clear();
            resultBean.addAll(photoPath);
            resultBean.remove(0);
            DetailActivity.currentPosition = psn;
            DetailActivity.currentPsn = psn - 1;
            Intent intent = new Intent(AlbumActivity.this, DetailActivity.class);
            intent.putExtra("urlExtra" , (Serializable) resultBean);
            ShareElementUtils.transToNextWithElement(AlbumActivity.this , intent , view , getResources().getString(R.string.share_photo_detail));
        }else {
            selectPhoto(psn);
        }
    }

    private void selectPhoto(int psn){
        PhotoBean photoBean = photoPath.get(psn);
        if (photoBean.isSelected()){
            photoBean.setSelected(!photoBean.isSelected());
            for (int i = 0 ; i < currentPickPhotoList.size() ; i ++){
                if (currentPickPhotoList.get(i).getPath().equals(photoBean.getPath())){
                    currentPickPhotoList.remove(i);
                }
            }
        }else {
            if (currentPickPhotoList.size() < pickPhotoNums){
                photoBean.setSelected(!photoBean.isSelected());
                currentPickPhotoList.add(photoBean);
            }else {
                Toast.makeText(this, "图片达到最大可选数量", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        albumAdapter.notifyItemChanged(psn);
    }

    /**
     * 调用系统相机拍照
     */
    @Override
    public void onCameraClick() {
        requestCameraPermission();
    }

    /**
     * 滑动到图片相应位置
     */
    @Override
    public void onPhotoDetailSelected(int psn) {
        recycler.smoothScrollToPosition(psn);
    }

    @Override
    public void onPermissionGranted(int requestCode , List<String> allGrantedPermission) {
        switch (requestCode){
            case Configs.READ_PERMISSION_CODE:
                AlbumAsync albmAsyn = new AlbumAsync(this);
                albmAsyn.execute("");
                albmAsyn.setOnPhotoLoadListener(this);
                break;
            case Configs.CAMERA_PERMISSION_CODE:
                tempFile = CommonUtils.createTempFile();
                CommonUtils.toSystemPhotoCapturer(this , Configs.CAMERA_SHOT_REQUEST_CODE , tempFile);
                break;
        }
    }

    @Override
    public void onPermissionDenied(int requestCode , List<String> deniedPermissions) {
        switch (requestCode){
            case Configs.READ_PERMISSION_CODE:
                showSettingDialog(this);
                break;
            case Configs.CAMERA_PERMISSION_CODE:
                showSettingDialog(this);
                break;
        }
    }

    private void requestReadPermission(){
        requestSelfPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE , Manifest.permission.WRITE_EXTERNAL_STORAGE} , Configs.READ_PERMISSION_CODE , this);
    }

    private void requestCameraPermission(){
        requestSelfPermissions(new String[]{Manifest.permission.CAMERA} , Configs.CAMERA_PERMISSION_CODE , this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK){
            if (requestCode ==Configs. CAMERA_SHOT_REQUEST_CODE){
                if (tempFile != null){
                    Uri uri = Uri.fromFile(tempFile);
                    AlbumActivity.this.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri));
                    PhotoBean photoBean = new PhotoBean();
                    photoBean.setSelected(false);
                    photoBean.setPath(tempFile.getAbsolutePath());
                    photoPath.add(1 , photoBean);
                    albumAdapter.notifyDataSetChanged();
                }
            }
        }else {
            if (tempFile != null){
                tempFile.delete();
            }
        }
    }

    @Override
    public void onClick(View view) {
        int i = view.getId();
        if (i == R.id.album_title_finish) {
            if (selectPhotoResult == null){
                Log.e(TAG, "onClick: SelectPhotoResult is null");
                return;
            }
            listExtra.clear();
            for (PhotoBean bean : currentPickPhotoList) {
                listExtra.add(bean.getPath());
            }
            selectPhotoResult.onReceivePhotoResult(listExtra);
            selectPhotoResult = null;
            finish();
        }else if (i == R.id.album_title_back){
            finish();
        }
    }

    public static void toAlbumActivity(Context context , int pickPhotoNums , ArrayList<String> list , int selectImg , int unSelectImg , int camearImg , int titleView , boolean isInDetailpage){
        Intent intent = new Intent(context , AlbumActivity.class);
        intent.putExtra(Configs.PICK_PHOTO_NUMS_FLAG , pickPhotoNums);
        intent.putStringArrayListExtra(Configs.ALREADY_PICK_PHOTO_LIST , list);
        intent.putExtra(Configs.ADAPTER_SELECT_IMG , selectImg);
        intent.putExtra(Configs.ADAPTER_UNSELECT_IMG , unSelectImg);
        intent.putExtra(Configs.ADAPTER_CAMEAR_IMG , camearImg);
        intent.putExtra(Configs.ALBUM_TITLE_VIEW , titleView);
        intent.putExtra(Configs.ALBUM_IS_IN_DETAIL_PAGE , isInDetailpage);
        context.startActivity(intent);
    }

    public static void setOnSelectResultListener(SelectPhotoResult selectResult){
        selectPhotoResult = selectResult;
    }

    private View getViewByPsn(int psn){
        for (int i = 0 ; i < recycler.getChildCount() ; i ++){
            View childAt = recycler.getChildAt(i);
            if (psn == (int) childAt.getTag(R.id.album_item_select)){
                return childAt;
            }
        }
        return null;
    }
}
