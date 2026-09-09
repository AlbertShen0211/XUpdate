package com.xuexiang.xupdatedemo.fragment;

import static android.app.Activity.RESULT_OK;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.view.View;

import com.xuexiang.xaop.annotation.Permission;
import com.xuexiang.xaop.annotation.SingleClick;
import com.xuexiang.xaop.consts.PermissionConsts;
import com.xuexiang.xpage.annotation.Page;
import com.xuexiang.xpage.base.XPageFragment;
import com.xuexiang.xupdate._XUpdate;
import com.xuexiang.xupdatedemo.R;
import com.xuexiang.xupdatedemo.databinding.FragmentFileMd5Binding;
import com.xuexiang.xutil.app.IntentUtils;
import com.xuexiang.xutil.app.PathUtils;
import com.xuexiang.xutil.app.SocialShareUtils;
import com.xuexiang.xutil.common.StringUtils;
import com.xuexiang.xutil.file.FileUtils;
import com.xuexiang.xutil.tip.ToastUtils;

/**
 * @author xuexiang
 * @since 2019-09-03 23:45
 */
@Page(name = "获取文件的MD5值")
public class FileMD5Fragment extends XPageFragment {

    private static final int REQUEST_CODE_SELECT_APK_FILE = 1000;

    private FragmentFileMd5Binding binding;

    @Override
    protected int getLayoutId() {
        return R.layout.fragment_file_md5;
    }

    @Override
    protected void initViews() {
        binding = FragmentFileMd5Binding.bind(mRootView);
    }

    @Override
    protected void initListeners() {
        binding.btnSelectFile.setOnClickListener(v -> selectAPKFile());
        binding.btnCalculateMd5.setOnClickListener(v -> calculateMd5());
        binding.btnShareFile.setOnClickListener(v -> shareFile());
        binding.btnShareMd5.setOnClickListener(v -> shareMd5());
    }

    @SingleClick
    private void calculateMd5() {
        String filePath = binding.tvPath.getText().toString();
        if (StringUtils.isEmpty(filePath)) {
            ToastUtils.toast("请先选择文件！");
            return;
        }

        binding.tvMd5.setText(_XUpdate.encryptFile(FileUtils.getFileByPath(filePath)));
        binding.tvSize.setText(String.valueOf(FileUtils.getFileLength(filePath) / 1024));
    }

    @SingleClick
    private void shareFile() {
        String filePath = binding.tvPath.getText().toString();
        if (StringUtils.isEmpty(filePath)) {
            ToastUtils.toast("请先选择文件！");
            return;
        }

        SocialShareUtils.shareFile(getActivity(), PathUtils.getUriForFile(FileUtils.getFileByPath(filePath)));
    }

    @SingleClick
    private void shareMd5() {
        String md5 = binding.tvMd5.getText().toString();
        if (StringUtils.isEmpty(md5)) {
            ToastUtils.toast("请先计算MD5值！");
            return;
        }

        shareText(md5);
    }

    /**
     * 分享文字
     *
     * @param content 文字
     */
    private void shareText(String content) {
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_TEXT, content);
        shareIntent.setType("text/plain");
        //设置分享列表的标题，并且每次都显示分享列表
        startActivity(Intent.createChooser(shareIntent, "分享到"));
    }

    @Permission(PermissionConsts.STORAGE)
    private void selectAPKFile() {
        startActivityForResult(IntentUtils.getDocumentPickerIntent(IntentUtils.DocumentType.ANY), REQUEST_CODE_SELECT_APK_FILE);
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_CODE_SELECT_APK_FILE) {
                binding.tvPath.setText(PathUtils.getFilePathByUri(getContext(), data.getData()));
            }
        }
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
