package com.termux.app.ui;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import com.termux.R;

/** Generic, original glyphs used for packages and command templates. */
public final class Icons {

    public static final class PackageIcon {
        @DrawableRes public final int resId;
        @ColorRes public final int colorRes;
        PackageIcon(int resId, int colorRes) { this.resId = resId; this.colorRes = colorRes; }
    }

    private Icons() { }

    @NonNull
    public static PackageIcon forPackage(@NonNull String name) {
        String n = name.toLowerCase();
        if (n.startsWith("python") || n.equals("nodejs") || n.startsWith("clang") || n.equals("golang") || n.equals("rust"))
            return new PackageIcon(R.drawable.ic_code, R.color.tx_accent);
        if (n.startsWith("git")) return new PackageIcon(R.drawable.ic_branch, R.color.tx_orange);
        if (n.startsWith("docker") || n.startsWith("proot")) return new PackageIcon(R.drawable.ic_container, R.color.tx_accent);
        if (n.equals("neofetch") || n.equals("htop") || n.equals("btop")) return new PackageIcon(R.drawable.ic_monitor, R.color.tx_text);
        if (n.equals("vim") || n.equals("nano") || n.startsWith("neovim") || n.equals("emacs")) return new PackageIcon(R.drawable.ic_edit, R.color.tx_green);
        if (n.equals("curl") || n.equals("wget") || n.startsWith("openssh") || n.equals("nmap")) return new PackageIcon(R.drawable.ic_globe, R.color.tx_text);
        return new PackageIcon(R.drawable.ic_cube, R.color.tx_text_2);
    }

    @DrawableRes
    public static int byName(String key) {
        if (key == null) return R.drawable.ic_prompt;
        switch (key) {
            case "code": return R.drawable.ic_code;
            case "branch": return R.drawable.ic_branch;
            case "container": return R.drawable.ic_container;
            case "globe": return R.drawable.ic_globe;
            case "info": return R.drawable.ic_info;
            case "monitor": return R.drawable.ic_monitor;
            default: return R.drawable.ic_prompt;
        }
    }
}
