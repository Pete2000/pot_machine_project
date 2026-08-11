#include <jni.h>
#include <termios.h>
#include <unistd.h>

#include <ctype.h>
#include <errno.h>
#include <stdio.h>
#include <string.h>

static speed_t baud_to_constant(jint baud_rate) {
    switch (baud_rate) {
        case 1200: return B1200;
        case 2400: return B2400;
        case 4800: return B4800;
        case 9600: return B9600;
        case 19200: return B19200;
        case 38400: return B38400;
#ifdef B57600
        case 57600: return B57600;
#endif
#ifdef B115200
        case 115200: return B115200;
#endif
        default: return B19200;
    }
}

static int get_fd(JNIEnv* env, jobject file_descriptor) {
    jclass fd_class = (*env)->FindClass(env, "java/io/FileDescriptor");
    if (fd_class == NULL) {
        return -1;
    }
    jfieldID descriptor_field = (*env)->GetFieldID(env, fd_class, "descriptor", "I");
    if (descriptor_field == NULL) {
        (*env)->ExceptionClear(env);
        return -1;
    }
    return (*env)->GetIntField(env, file_descriptor, descriptor_field);
}

static void throw_io_exception(JNIEnv* env, const char* prefix) {
    char message[256];
    snprintf(message, sizeof(message), "%s: %s", prefix, strerror(errno));
    jclass exception_class = (*env)->FindClass(env, "java/io/IOException");
    if (exception_class != NULL) {
        (*env)->ThrowNew(env, exception_class, message);
    }
}

static void set_data_bits(struct termios* tty, jint data_bits) {
    tty->c_cflag &= ~CSIZE;
    tty->c_cflag |= data_bits == 7 ? CS7 : CS8;
}

static void set_stop_bits(struct termios* tty, jint stop_bits) {
    if (stop_bits >= 2) {
        tty->c_cflag |= CSTOPB;
    } else {
        tty->c_cflag &= ~CSTOPB;
    }
}

static void set_parity(struct termios* tty, const char* parity_name) {
    char parity[16] = {0};
    if (parity_name != NULL) {
        size_t length = strlen(parity_name);
        if (length >= sizeof(parity)) {
            length = sizeof(parity) - 1;
        }
        for (size_t i = 0; i < length; ++i) {
            parity[i] = (char)toupper((unsigned char)parity_name[i]);
        }
    }

    if (strcmp(parity, "EVEN") == 0) {
        tty->c_cflag |= PARENB;
        tty->c_cflag &= ~PARODD;
    } else if (strcmp(parity, "ODD") == 0) {
        tty->c_cflag |= PARENB;
        tty->c_cflag |= PARODD;
    } else {
        tty->c_cflag &= ~PARENB;
        tty->c_cflag &= ~PARODD;
    }
}

JNIEXPORT jstring JNICALL
Java_com_example_plccontroller_data_plc_NativeSerialPortConfigurator_nativeConfigureBinary(
    JNIEnv* env,
    jobject instance,
    jobject file_descriptor,
    jint baud_rate,
    jint data_bits,
    jint stop_bits,
    jstring parity_name
) {
    (void)instance;

    int fd = get_fd(env, file_descriptor);
    if (fd < 0) {
        jclass exception_class = (*env)->FindClass(env, "java/io/IOException");
        if (exception_class != NULL) {
            (*env)->ThrowNew(env, exception_class, "Unable to resolve serial FileDescriptor");
        }
        return NULL;
    }

    struct termios tty;
    memset(&tty, 0, sizeof(tty));
    if (tcgetattr(fd, &tty) != 0) {
        throw_io_exception(env, "tcgetattr failed");
        return NULL;
    }

    speed_t speed = baud_to_constant(baud_rate);
    if (cfsetispeed(&tty, speed) != 0 || cfsetospeed(&tty, speed) != 0) {
        throw_io_exception(env, "cfset speed failed");
        return NULL;
    }

    const char* parity_chars = (*env)->GetStringUTFChars(env, parity_name, NULL);

    tty.c_iflag = 0;
    tty.c_oflag = 0;
    tty.c_lflag = 0;
    tty.c_cflag |= CLOCAL | CREAD;
    set_data_bits(&tty, data_bits);
    set_stop_bits(&tty, stop_bits);
    set_parity(&tty, parity_chars);

#ifdef CRTSCTS
    tty.c_cflag &= ~CRTSCTS;
#endif
#ifdef HUPCL
    tty.c_cflag &= ~HUPCL;
#endif

    tty.c_cc[VMIN] = 0;
    tty.c_cc[VTIME] = 1;

    if (parity_chars != NULL) {
        (*env)->ReleaseStringUTFChars(env, parity_name, parity_chars);
    }

    if (tcflush(fd, TCIOFLUSH) != 0) {
        throw_io_exception(env, "tcflush failed");
        return NULL;
    }

    if (tcsetattr(fd, TCSANOW, &tty) != 0) {
        throw_io_exception(env, "tcsetattr failed");
        return NULL;
    }

    struct termios applied;
    memset(&applied, 0, sizeof(applied));
    if (tcgetattr(fd, &applied) != 0) {
        throw_io_exception(env, "tcgetattr after set failed");
        return NULL;
    }

    char description[256];
    snprintf(
        description,
        sizeof(description),
        "iflag=0x%lx oflag=0x%lx cflag=0x%lx lflag=0x%lx vmin=%d vtime=%d",
        (unsigned long)applied.c_iflag,
        (unsigned long)applied.c_oflag,
        (unsigned long)applied.c_cflag,
        (unsigned long)applied.c_lflag,
        (int)applied.c_cc[VMIN],
        (int)applied.c_cc[VTIME]
    );
    return (*env)->NewStringUTF(env, description);
}
