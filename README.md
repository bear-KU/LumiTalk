# LumiTalk
Android application for LED-based communication.

## Requirements
- Android Studio (with Android NDK installed)
- OpenCV Android SDK 4.12.0

## Setup

### 1. Clone the repository
```bash
git clone https://github.com/YOUR_USERNAME/lumitalk.git
```

### 2. Download OpenCV Android SDK
Download OpenCV Android SDK 4.12.0 from https://opencv.org/releases/ and extract it to any directory.
```bash
unzip OpenCV-android-sdk-4.12.0-android-sdk.zip -d path/to/your/

# Example
# unzip OpenCV-android-sdk-4.12.0-android-sdk.zip -d ~/develop/
```

### 3. Create symbolic link
Create a symbolic link to the OpenCV native libs directory:
```bash
cd path/to/lumitalk/app/src/main
ln -s path/to/your/OpenCV-android-sdk/sdk/native/libs jniLibs

# Example
# ln -s ~/develop/OpenCV-android-sdk/sdk/native/libs jniLibs
```

### 4. Set environment variable
Add the following to your `~/.bashrc` or `~/.zshrc`:
```bash
# Set the path to your OpenCV Android SDK
export OPENCV_ANDROID_SDK="path/to/your/OpenCV-android-sdk"

# Example
# export OPENCV_ANDROID_SDK="$HOME/develop/OpenCV-android-sdk"
```

Then reload:
```bash
source ~/.bashrc
```

### 5. Copy libc++_shared.so
Copy `libc++_shared.so` from the NDK to the OpenCV libs directory.
The NDK is usually located at `~/Android/Sdk/ndk/<version>/`.
```bash
cp path/to/your/Android/Sdk/ndk/<version>/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so \
   path/to/your/OpenCV-android-sdk/sdk/native/libs/arm64-v8a/

# Example
# cp ~/Android/Sdk/ndk/28.2.13676358/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so \
#    ~/develop/OpenCV-android-sdk/sdk/native/libs/arm64-v8a/
```

### 6. Build and install
```bash
./gradlew installDebug
```
