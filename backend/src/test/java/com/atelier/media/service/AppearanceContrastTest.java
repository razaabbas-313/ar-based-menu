package com.atelier.media.service;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class AppearanceContrastTest {
 @Test void accentUsedForSmallMenuLabelsMustMeetTextContrastOnEverySurface(){
  var values=new HashMap<String,String>();values.putAll(Map.of("preset","MINIMAL","accent","#dd2200","background","#ffffff","surface","#f5f5f5","text","#111111","heading","SANS","layout","cards","category","pills","button","rounded","motion","subtle"));values.put("card","soft");
  assertThatThrownBy(()->ProductStudioService.validate(values)).isInstanceOf(com.atelier.media.exception.InvalidFieldException.class);
 }
}
