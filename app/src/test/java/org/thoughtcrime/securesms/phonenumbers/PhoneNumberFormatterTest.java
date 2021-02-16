package org.thoughtcrime.securesms.phonenumbers;


import org.junit.Before;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;

public class PhoneNumberFormatterTest {

  @Before
  public void setup() {}

  @Test
  public void testAddressString() throws Exception {
    PhoneNumberFormatter formatter = new PhoneNumberFormatter("+14152222222");
    assertEquals(formatter.format("bonbon"), "bonbon");
  }
  
  // New
  @Test
  public void testADifferentLanguage() throws Exception {
    PhoneNumberFormatter formatter = new PhoneNumberFormatter("+14152222222");
    assertEquals(formatter.format("شماره تلفن"), "شماره تلفن");
  }

  @Test
  public void testAddressShortCode() throws Exception {
    PhoneNumberFormatter formatter = new PhoneNumberFormatter("+14152222222");
    assertEquals(formatter.format("40404"), "40404");
  }

  @Test
  public void testEmailAddress() throws Exception {
    PhoneNumberFormatter formatter = new PhoneNumberFormatter("+14152222222");
    assertEquals(formatter.format("junk@junk.net"), "junk@junk.net");
  }

  @Test
  public void testNumberArbitrary() throws Exception {
    PhoneNumberFormatter formatter = new PhoneNumberFormatter("+14152222222");
    assertEquals(formatter.format("(415) 111-1122"), "+14151111122");
    assertEquals(formatter.format("(415) 111 1123"), "+14151111123");
    assertEquals(formatter.format("415-111-1124"), "+14151111124");
    assertEquals(formatter.format("415.111.1125"), "+14151111125");
    assertEquals(formatter.format("+1 415.111.1126"), "+14151111126");
    assertEquals(formatter.format("+1 415 111 1127"), "+14151111127");
    assertEquals(formatter.format("+1 (415) 111 1128"), "+14151111128");
    assertEquals(formatter.format("911"), "911");
    assertEquals(formatter.format("+456-7890"), "+4567890");
    // New
    assertEquals(formatter.format("+1 415,111,1129"), "+14151111129");
    assertEquals(formatter.format("+1 415*111*1130"), "+14151111130");
    assertEquals(formatter.format("+1 415_111_1131"), "+14151111131");
    assertEquals(formatter.format("+1 415          111          1132"), "+14151111132");

    formatter = new PhoneNumberFormatter("+442079460010");
    assertEquals(formatter.format("(020) 7946 0018"), "+442079460018");
  }

  @Test
  public void testUsNumbers() {
    PhoneNumberFormatter formatter = new PhoneNumberFormatter("+16105880522");

    assertEquals("+551234567890", formatter.format("+551234567890"));
    assertEquals("+11234567890", formatter.format("(123) 456-7890"));
    assertEquals("+11234567890", formatter.format("1234567890"));
    assertEquals("+16104567890", formatter.format("456-7890"));
    assertEquals("+16104567890", formatter.format("4567890"));
    assertEquals("+11234567890", formatter.format("011 1 123 456 7890"));
    assertEquals("+5511912345678", formatter.format("0115511912345678"));
    assertEquals("+16105880522", formatter.format("+16105880522"));
    // New
    assertEquals("0", formatter.format("0"));
    assertEquals("+16105880522", formatter.format("+16105880522"));
    assertEquals("+161058805223", formatter.format("+161058805223"));
    assertEquals("+1610588052234", formatter.format("+1610588052234"));
    assertEquals("+161058805223459", formatter.format("+161058805223459"));
    assertEquals("+1610588052234590", formatter.format("+1610588052234590"));
  }

  @Test
  public void testBrNumbers() {
    PhoneNumberFormatter formatter = new PhoneNumberFormatter("+5521912345678");

    assertEquals("+16105880522", formatter.format("+16105880522"));
    assertEquals("+552187654321", formatter.format("8765 4321"));
    assertEquals("+5521987654321", formatter.format("9 8765 4321"));
    assertEquals("+552287654321", formatter.format("22 8765 4321"));
    assertEquals("+5522987654321", formatter.format("22 9 8765 4321"));
    assertEquals("+551234567890", formatter.format("+55 (123) 456-7890"));
    assertEquals("+14085048577", formatter.format("002214085048577"));
    assertEquals("+5511912345678", formatter.format("011912345678"));
    assertEquals("+5511912345678", formatter.format("02111912345678"));
    assertEquals("+551234567", formatter.format("1234567"));
    assertEquals("+5521912345678", formatter.format("+5521912345678"));
    assertEquals("+552112345678", formatter.format("+552112345678"));
  }

  @Test
  public void testGroup() throws Exception {
    PhoneNumberFormatter formatter = new PhoneNumberFormatter("+14152222222");
    assertEquals(formatter.format("__textsecure_group__!foobar"), "__textsecure_group__!foobar");
  }

  @Test
  public void testLostLocalNumber() throws Exception {
    PhoneNumberFormatter formatter = new PhoneNumberFormatter("US", true);
    assertEquals(formatter.format("(415) 111-1122"), "+14151111122");
  }

  @Test
  public void testInvalidNumber() {
    try {
      PhoneNumberFormatter formatter = new PhoneNumberFormatter("1");
    }
    catch(AssertionError e){
    }
  }

  @Test
  public void testPrettyPrint() throws Exception{
    PhoneNumberFormatter formatter = new PhoneNumberFormatter("+15555555555");
    // omit superfluous characters with substring
    assertEquals("(555) 555-5555", formatter.prettyPrintFormat("+15555555555").substring(1,15));
  }

}
