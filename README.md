
# Android Edge Agent

## A. Introduction
Android Edge Agent is an Android application to run EPI client.

This project uses the official Node.js codebase, built for Android systems (with various architectures) using [Termux](https://termux.dev/en/) - an open source project that emulates a terminal and provides a Linux environment on Android.

### Termux

Termux allows to build a variety of packages and generate working binaries for usages in Android applications.

[Termux packages](https://github.com/termux/termux-packages) contains scripts and patches to build packages for the Termux Android application.

Termux provides a Docker container with a build environment which is the easiest and fastest way to start building packages. Inside the container we can find a file structure that mimics a real Android environment. 


### Building packages with Termux

A video including the steps needed to setup the environment and build any Termux package, including build options details can be found [here](https://github.com/termux/termux-packages/wiki/Building-packages).

The packages can also be built on Windows environment by using WSL 2, but every step starting from cloning the [termux-packages](https://github.com/termux/termux-packages) git project must be done on the WSL 2 terminal. The complete build flow was executed successfully on Windows WSL 2 using Debian image.

Install steps (running on WSL 2 if on Windows):
1. Clone [termux-packages](https://github.com/termux/termux-packages) git project: 
```
    git clone https://github.com/termux/termux-packages
```
2. Navigate to `termux-packages/nodejs/build.sh` to check the Node.js version that will be built, by checking the values specified inside the TERMUX_PKG_VERSION variable (e.g. 18.7.0). This can be adjusted accordingly with the desired version that needs to be built.
3. Start the termux docker instance by executing the script `./termux-packages/scripts/run-docker.sh` from the previously cloned git project. This will start the Termux docker instance (considering that Docker is correctly installed on the machine, e.g. Docker Windows if running on Windows WSL 2).
4. After successfully executing the previous command, the bash interface is now switched inside the termux image. Any time access is required on the termux image instance the previous step needs to be executed (it will access the existing image instance if it already exists). Once inside the termux instance, make sure that the current working directory is `/home/builder/termux-packages`.
5. To generate the Node.js build, the **ONE** of the following needs to be executed, based on the desired Android architecture that Node.js needs to be built for:
```
./build-package.sh nodejs -a aarch64
./build-package.sh nodejs -a arm
./build-package.sh nodejs -a x86_64
./build-package.sh nodejs -a i686
```

```
aarch64 corresponds to arm64-v8a Android architecture (64-bit ARM architecture)
arm corresponds to armeabi-v7a Android architecture (32-bit ARM architecture)
x86_64 corresponds to x86_64 Android architecture (64-bit x86 architecture)
i686 corresponds to x86 Android architecture (32-bit x86 architecture)
```

Note: only one architecture needs to be executed at a time, since the output is generated at the same location (`/data/data/com.termux/files/usr`) invariant of the specified architecture. Be aware that this location is available only from the termux image instance. Also be aware that the build process is very resource and time consuming (~45 minutes on Windows WSL 2 with Intel(R) Core(TM) i7-7700K CPU @ 4.20GHz staying in 100% usages, without executing anything else).

6. In order to copy anything from docker container to the local file system we need to run the following command in order to get the container id:
```
docker ps
```
Then we can copy the contents from the container to the local file system using:
```
docker cp <container-id>:<src-path> <local-dest-path>
```
e.g.: `docker cp d0a0334f8971:/data/data/com.termux/files/usr D:\output`

7. The following generated files should be included (by overwriting the old ones) based on the specified architecture:
- bin/node: this represents the main **.so** file and should be copied inside the Android project at `app\libs\{ARCHITECTURE}\libnode.so` (the file needs to be renamed to `libnode` and have the `so` extension!).
- lib: this folder contains all the node dependencies and they also needs to be included inside the Android project in order for Node.js to run. The whole folder content needs to be copied to `app/src/main/assets/{ARCHITECTURE}`. Afterwards, the files having the `a` extension (e.g. libcrypto.a) need to be removed since they are not of use for Android - they represent static build result object (a represents archive since it's an archived object file). Next step is to remove the symlinks keep only the and original associated files which needs be renamed as `so` files; e.g. having symlinks `libicudata.so` and `libicudata.so.71` which ultimately points to `libicudata.so.71.1`, the symlinks should be deleted and the original file `libicudata.so.71.1` renamed to `libicudata.so`. But before deleting the symlinks this information should be recorded inside the `app/src/main/assets/symlinks.json` configuration file (in json format), since the original file names of the dependencies needs to be replicated on the Android application, when the application is first opened.

Considering the following output from termux built for the `lib` folder root files:
```
libc++_shared.so
libcares.so
libcrypto.a
libcrypto.so
libcrypto.so.3
libicudata.a
libicudata.so
libicudata.so.71
libicudata.so.71.1
libicui18n.a
libicui18n.so
libicui18n.so.71
libicui18n.so.71.1
libicuio.a
libicuio.so
libicuio.so.71
libicuio.so.71.1
libicutest.a
libicutest.so
libicutest.so.71
libicutest.so.71.1
libicutu.a
libicutu.so
libicutu.so.71
libicutu.so.71.1
libicuuc.a
libicuuc.so
libicuuc.so.71
libicuuc.so.71.1
libssl.a
libssl.so
libssl.so.3
libutil.so
libz.a
libz.so
libz.so.1
libz.so.1.2.12
```

Will need to have the following files inside `app/src/main/assets/{ARCHITECTURE}`:
```
libc++_shared.so
libcares.so
libcrypto.so
libicudata.so
libicui18n.so
libicuio.so
libicutest.so
libicutu.so
libicuuc.so
libssl.so
libutil.so
libz.so
```

And the associated `app/src/main/assets/symlinks.json` configuration file:
```
[
  { "originalFile": "libcrypto.so", "symlinkName": "libcrypto.so.3" },
  { "originalFile": "libicudata.so", "symlinkName": "libicudata.so.71" },
  { "originalFile": "libicudata.so", "symlinkName": "libicudata.so.71.1" },
  { "originalFile": "libicui18n.so", "symlinkName": "libicui18n.so.71" },
  { "originalFile": "libicui18n.so", "symlinkName": "libicui18n.so.71.1" },
  { "originalFile": "libicuio.so", "symlinkName": "libicuio.so.71" },
  { "originalFile": "libicuio.so", "symlinkName": "libicuio.so.71.1" },
  { "originalFile": "libicutest.so", "symlinkName": "libicutest.so.71" },
  { "originalFile": "libicutest.so", "symlinkName": "libicutest.so.71.1" },
  { "originalFile": "libicutu.so", "symlinkName": "libicutu.so.71" },
  { "originalFile": "libicutu.so", "symlinkName": "libicutu.so.71.1" },
  { "originalFile": "libicuuc.so", "symlinkName": "libicuuc.so.71" },
  { "originalFile": "libicuuc.so", "symlinkName": "libicuuc.so.71.1" },
  { "originalFile": "libssl.so", "symlinkName": "libssl.so.3" },
  { "originalFile": "libz.so", "symlinkName": "libz.so.1" },
  { "originalFile": "libz.so", "symlinkName": "libz.so.1.2.12" }
]
```


At the time of writing the Node.js version is 18.7.0 and it was built for all four architectures.

This will basically install Node inside the Android application and
make it run on a free port for the application to connect to.

**Warning**: Please be sure that you have at least 15GB free space on the device you want to make a build.

## B. Build it with Android Studio

### Step 1 - Have Java Development Kit installed

You must have JDK 11 installed. (Probably other versions of JDK - down to 8 - might work as we do not use specific JDK 11 features
but we built it and test it using verison 11)

To see if you have java installed and what version run
```
java --version
```

You should get something like:

![alt text](./java-version-info.png "Webview Debugging")


If you do not have JDK installed then follow this link - [How to install JDK](https://docs.oracle.com/en/java/javase/11/install/index.html) - to see how to install it on your machine.


### Step 2 - Install Android Studio 4.1

Download version 4.1 from [Download Android Studio](https://developer.android.com/studio) page

__Note__: Please try to to stick with 4.1 of the Studio as any new version (slight increase in version number) might convert and upgrade the project file(s) which might trigger a chain reaction of upgrades that might put whole project into a configration that was not tested yet.

### Step 3 - Install proper dependencies

#### Install NDK
    Menu > Tools > SDK Manager > SDK Tools > Show Package Details > NDK (Side by side) > 21.3.6528147

### Step 4 - Create/open project


### Step 5 - (Optional) Add the Nodejs project

__Note__: SKIP THIS STEP if you are using this repository from inside the [epi-workspace](https://github.com/PharmaLedger-IMI/epi-workspace)

#### a. Copy project's files

Copy project inside app/src/main/assets/**nodejs-project**/ folder


#### b. Bring in all project dependencies
```sh
cd  app/src/main/assets/nodejs-project/

npm install
```

### Step 6 - Create proper Android emulator
    Menu > Tools > AVD Manager > + Create Virtual Device... > Phone > Pixel 4 > (Next) >  Release Name (Pie) / API Level 28 > Next > AVD Name: Pixel 4 API 30 > (Finish)


### Step 7 - Run the project

Note: Set proper permissions for application: #App Info > Permissions > (3 dots) > All permissions



## C. Build it from console

### 1. Setup Android SDK

### i. Create Android SDK folder

Create a folder named android (we will use _${android_home}_ to refer to it) and subfolders.
```sh
    mkdir -p ~/${android_home}/sdk/cmdline-tools/latest/
```

#### ii. Download SDK

This is based on the Android SDK, so you need to download it 

Go to https://developer.android.com/studio#downloads and search for "SDK tools package" inside "Command line tools only" section.

#### iii. Unzip it into
Unzip the content of zip file into
```
${android_home}/sdk/cmdline-tools/latest/
```

Make sure you can run __sdkmanager__ from

```
${android_home}/sdk/cmdline-tools/latest/bin/
```

folder.

#### iv. Setup local environment values

Create a local file named **local.properties**
and add 
```
sdk.dir=~/${android__home}/sdk
```

replacing the right value with your path to SDK


### 2. Make sure you can run gradle

__Windows__
Run gradle.bat in Command Prompt

__Linux__
Run gradlew in console.

Tip: If gradlew is not running make it executable with

```sh
chmod +x gradlew
```

### See gradle available task
```sh
./gradle(w) tasks
```


### 3.Install NDK

Inside project's folder type:
```sh
./gradlew -b ndk.gradle installNDK
```


### 4. Build an APK

For debug version run

```sh
./gradlew assembleDebug
```

For release version run
```sh
./gradlew assembleRelease
```

This will create an .apk file inside `app/build/output/apk/debug` folder.

### 5. Install it

If beside building you want to run it on a device
```shell
./gradlew installDebug
```
This will install the application on the default (running) emulator but IT
WILL NOT LAUNCH IT FOR YOU.


### 6. Build a release APK
See this [link](https://developer.android.com/studio/build/building-cmdline#ReleaseMode)


### 7. Signing an APK
By default the release version of the APK is signed.

To make a release APK run
```sh
./gradlew assembleRelease
```

By default a keystore file (_app/epi.jks_) is used and a default credentials properties file (_app/sign.settings_). They are created by default.

To change the password keystore and access credentials to it you 
have to update those files.




## D. Troubleshooting

You can debug the inner browser (WebView) by typing

```
chrome://inspect
```

inside your Chrome browser.

You will get something like

![alt text](./webview-debugging.png "Webview Debugging")


### Open

More information here: [Remote Debugging WebViews](https://developers.google.com/web/tools/chrome-devtools/remote-debugging/webviews)
