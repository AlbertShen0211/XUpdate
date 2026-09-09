package com.xuexiang.xupdatedemo.fragment;

import android.text.TextUtils;

import com.xuexiang.xpage.annotation.Page;
import com.xuexiang.xpage.base.XPageFragment;
import com.xuexiang.xupdate.XUpdate;
import com.xuexiang.xupdatedemo.R;
import com.xuexiang.xupdatedemo.databinding.FragmentXupdateServiceBinding;
import com.xuexiang.xupdatedemo.custom.XUpdateServiceParser;
import com.xuexiang.xupdatedemo.utils.SettingSPUtils;
import com.xuexiang.xutil.app.PathUtils;
import com.xuexiang.xutil.net.NetworkUtils;

import java.util.List;

import okhttp3.HttpUrl;

/**
 * @author xuexiang
 * @since 2018/7/30 上午11:39
 */
@Page(name = "版本更新服务")
public class XUpdateServiceFragment extends XPageFragment {

    private FragmentXupdateServiceBinding binding;

    @Override
    protected int getLayoutId() {
        return R.layout.fragment_xupdate_service;
    }

    @Override
    protected void initViews() {
        binding = FragmentXupdateServiceBinding.bind(mRootView);
        binding.etServiceUrl.setText(SettingSPUtils.get().getServiceURL());
    }

    @Override
    protected void initListeners() {
        binding.btnSave.setOnClickListener(v -> {
            String url = binding.etServiceUrl.getText().toString().trim();
            if (NetworkUtils.isUrlValid(url) && parseBaseUrl(url)) {
                SettingSPUtils.get().setServiceURL(url);
            }
        });
        binding.btnUpdate.setOnClickListener(v ->
                XUpdate.newBuild(getContext())
                        .apkCacheDir(PathUtils.getExtDownloadsPath())
                        .updateHttpService(XUpdateServiceParser.getUpdateHttpService())
                        .isGet(false)
                        .updateUrl(XUpdateServiceParser.getVersionCheckUrl())
                        .updateParser(new XUpdateServiceParser())
                        .update()
        );
        binding.btnAutoUpdate.setOnClickListener(v ->
                XUpdate.newBuild(getContext())
                        .isGet(false)
                        .updateUrl(XUpdateServiceParser.getVersionCheckUrl())
                        .updateParser(new XUpdateServiceParser())
                        //如果需要完全无人干预，自动更新，需要root权限【静默安装需要】
                        .isAutoMode(true)
                        .update()
        );
        binding.btnForceUpdate.setOnClickListener(v ->
                XUpdate.newBuild(getContext())
                        .isGet(false)
                        .param("appKey", "test3")
                        .updateUrl(XUpdateServiceParser.getVersionCheckUrl())
                        .updateParser(new XUpdateServiceParser())
                        .update()
        );
    }

    /**
     * 解析baseUrl
     *
     * @param baseUrl
     * @return true: 设置baseUrl成功
     */
    public static boolean parseBaseUrl(String baseUrl) {
        if (!TextUtils.isEmpty(baseUrl)) {
            HttpUrl httpUrl = HttpUrl.parse(baseUrl);
            if (httpUrl != null) {
                List<String> pathSegments = httpUrl.pathSegments();
                return "".equals(pathSegments.get(pathSegments.size() - 1));
            }
        }
        return false;
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
