package com.xuexiang.xupdatedemo.activity;

import android.app.Activity;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.xuexiang.xpage.annotation.Page;
import com.xuexiang.xupdate.XUpdate;
import com.xuexiang.xupdatedemo.Constants;
import com.xuexiang.xupdatedemo.databinding.ActivityUpdateBinding;

/**
 * @author xuexiang
 * @since 2018/7/24 上午10:38
 */
@Page(name = "版本更新")
public class UpdateActivity extends Activity {

    private ActivityUpdateBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityUpdateBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnUpdate.setOnClickListener(v ->
                XUpdate.newBuild(UpdateActivity.this)
                        .updateUrl(Constants.DEFAULT_UPDATE_URL)
                        .update()
        );
        binding.btnSupportBackgroundUpdate.setOnClickListener(v ->
                XUpdate.newBuild(UpdateActivity.this)
                        .updateUrl(Constants.DEFAULT_UPDATE_URL)
                        .promptWidthRatio(0.7F)
                        .supportBackgroundUpdate(true)
                        .update()
        );
        binding.btnAutoUpdate.setOnClickListener(v ->
                XUpdate.newBuild(UpdateActivity.this)
                        .updateUrl(Constants.DEFAULT_UPDATE_URL)
                        //如果需要完全无人干预，自动更新，需要root权限【静默安装需要】
                        .isAutoMode(true)
                        .update()
        );
        binding.btnForceUpdate.setOnClickListener(v ->
                XUpdate.newBuild(UpdateActivity.this)
                        .updateUrl(Constants.FORCED_UPDATE_URL)
                        .update()
        );
    }

    @Override
    protected void onDestroy() {
        binding = null;
        super.onDestroy();
    }
}
