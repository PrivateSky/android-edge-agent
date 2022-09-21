GENCCODE_ASSEMBLY_TYPE=-a gcc
SO=so
SOBJ=so
A=a
LIBPREFIX=lib
LIB_EXT_ORDER=.71.1
COMPILE=arm-linux-androideabi-clang -I/data/data/com.termux/files/usr/include -D_REENTRANT  -DU_HAVE_ELF_H=1 -DU_HAVE_STRTOD_L=0 -DU_HAVE_STRING_VIEW=1  -DU_ATTRIBUTE_DEPRECATED= -march=armv7-a -mfpu=neon -mfloat-abi=softfp -mthumb -fstack-protector-strong -Oz -std=c11 -Wall -pedantic -Wshadow -Wpointer-arith -Wmissing-prototypes -Wwrite-strings   -c
LIBFLAGS=-I/data/data/com.termux/files/usr/include -DPIC -fPIC
GENLIB=arm-linux-androideabi-clang -march=armv7-a -mfpu=neon -mfloat-abi=softfp -mthumb -fstack-protector-strong -Oz -std=c11 -Wall -pedantic -Wshadow -Wpointer-arith -Wmissing-prototypes -Wwrite-strings   -L/data/data/com.termux/files/usr/lib -Wl,-rpath=/data/data/com.termux/files/usr/lib -march=armv7-a -fopenmp -static-openmp -Wl,--enable-new-dtags -Wl,--as-needed -Wl,-z,relro,-z,now  -shared -Wl,-Bsymbolic
LDICUDTFLAGS=-nodefaultlibs -nostdlib
LD_SONAME=-Wl,-soname -Wl,
RPATH_FLAGS=
BIR_LDFLAGS=-Wl,-Bsymbolic
AR=llvm-ar
ARFLAGS=r
RANLIB=llvm-ranlib
INSTALL_CMD=/usr/bin/install -c
