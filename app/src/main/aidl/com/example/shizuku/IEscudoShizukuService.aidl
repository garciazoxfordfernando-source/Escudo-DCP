package com.example.shizuku;

interface IEscudoShizukuService {
    void destroy() = 16777114;
    int getUid() = 1;
    boolean setPackageSuspended(String packageName, boolean suspended) = 2;
    boolean setPackageEnabled(String packageName, boolean enabled) = 3;
    boolean setPackageHidden(String packageName, boolean hidden) = 4;
    boolean setPermissionGranted(String packageName, String permission, boolean granted) = 5;
    String getLastError() = 6;
}
