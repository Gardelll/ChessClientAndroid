-keepclasseswithmembernames class * {
    native <methods>;
}

# Protobuf
-keep class * extends com.google.protobuf.GeneratedMessageLite {
  public static ** getDefaultInstance();
}
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
  <fields>;
}

# netty
-keepclassmembernames class io.netty.buffer.AbstractByteBufAllocator {
    *;
}

-keepclassmembernames class io.netty.buffer.AdvancedLeakAwareByteBuf {
    *;
}

-keepclassmembernames class io.netty.util.ReferenceCountUtil {
    *;
}
