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
-keepattributes Signature,InnerClasses

-keepclassmembernames class io.netty.buffer.AbstractByteBufAllocator {
    *;
}

-keepclassmembernames class io.netty.buffer.AdvancedLeakAwareByteBuf {
    *;
}

-keepclassmembers class io.netty.util.ReferenceCountUtil {
    *;
}

-keepclassmembernames class io.netty.util.ReferenceCountUtil {
    *;
}
