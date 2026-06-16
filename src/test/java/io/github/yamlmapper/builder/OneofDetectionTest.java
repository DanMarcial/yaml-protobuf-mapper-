package io.github.yamlmapper.builder;

import static org.junit.jupiter.api.Assertions.*;

import com.google.cloud.retail.v2.Product;
import com.google.cloud.retail.v2.UserEvent;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.OneofDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests for oneof field detection and conflict handling.
 */
class OneofDetectionTest {

  private static final Logger log = LoggerFactory.getLogger(OneofDetectionTest.class);

  private SetterResolver setterResolver;

  @BeforeEach
  void setUp() {
    setterResolver = new SetterResolver();
  }

  @Test
  void detectOneofFieldsInUserEvent() {
    log.info("=== UserEvent OneOf fields ===");
    for (OneofDescriptor oneof : UserEvent.getDescriptor().getOneofs()) {
      log.info("OneOf: {}", oneof.getName());
      for (FieldDescriptor field : oneof.getFields()) {
        log.info("  - {}", field.getName());
      }
    }
    log.info("Total oneofs: {}", UserEvent.getDescriptor().getOneofs().size());
  }

  @Test
  void detectOneofFieldsInProduct() {
    log.info("\n=== Product OneOf fields ===");
    for (OneofDescriptor oneof : Product.getDescriptor().getOneofs()) {
      log.info("OneOf: {}", oneof.getName());
      for (FieldDescriptor field : oneof.getFields()) {
        log.info("  - {}", field.getName());
      }
    }
    log.info("Total oneofs: {}", Product.getDescriptor().getOneofs().size());
  }

  @Test
  void getOneofInfo_returnsInfoForOneofField() {
    // Product has oneof "expiration" with fields "expire_time" and "ttl"
    Product.Builder builder = Product.newBuilder();

    SetterResolver.OneofInfo expireTimeInfo = setterResolver.getOneofInfo(builder, "expireTime");
    assertNotNull(expireTimeInfo);
    assertEquals("expiration", expireTimeInfo.oneofName());
    assertEquals("expire_time", expireTimeInfo.fieldName());

    SetterResolver.OneofInfo ttlInfo = setterResolver.getOneofInfo(builder, "ttl");
    assertNotNull(ttlInfo);
    assertEquals("expiration", ttlInfo.oneofName());
    assertEquals("ttl", ttlInfo.fieldName());
  }

  @Test
  void getOneofInfo_returnsNullForNonOneofField() {
    Product.Builder builder = Product.newBuilder();

    // "id" is not part of a oneof
    SetterResolver.OneofInfo idInfo = setterResolver.getOneofInfo(builder, "id");
    assertNull(idInfo);

    // "title" is not part of a oneof
    SetterResolver.OneofInfo titleInfo = setterResolver.getOneofInfo(builder, "title");
    assertNull(titleInfo);
  }

  @Test
  void getOneofInfo_returnsNullForNonExistentField() {
    Product.Builder builder = Product.newBuilder();

    SetterResolver.OneofInfo info = setterResolver.getOneofInfo(builder, "nonExistentField");
    assertNull(info);
  }
}
