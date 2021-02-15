package org.thoughtcrime.securesms.recipients;

import android.app.Application;
import android.content.Intent;
import android.provider.ContactsContract;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.profiles.ProfileName;
import org.thoughtcrime.securesms.testutil.LogRecorder;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.UUID;

import static android.provider.ContactsContract.Intents.Insert.EMAIL;
import static android.provider.ContactsContract.Intents.Insert.NAME;
import static android.provider.ContactsContract.Intents.Insert.PHONE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, application = Application.class)
public final class RecipientExporterTest {

  private static final int TEST_CACHE_LIMIT = 5;

  private RecipientIdCache recipientIdCache;
  private LogRecorder logRecorder;


  // added constructor test
  @Before
  public void setup() {
    recipientIdCache = new RecipientIdCache(TEST_CACHE_LIMIT);
    logRecorder      = new LogRecorder();
    Log.initialize(logRecorder);
  }

  @Test
  public void asAddContactIntent_with_null() {
    RecipientId recipientId = recipientIdCache.get(UUID.randomUUID(), null);
    Recipient recipient = new Recipient(recipientId);

    Intent intent = RecipientExporter.export(recipient).asAddContactIntent();

    assertEquals(Intent.ACTION_INSERT_OR_EDIT, intent.getAction());
    assertEquals(ContactsContract.Contacts.CONTENT_ITEM_TYPE, intent.getType());
    assertNull(intent.getStringExtra(NAME));
    assertNull(intent.getStringExtra(PHONE));//???
    assertNull(intent.getStringExtra(EMAIL));
  }

  @Test
  public void asAddContactIntent_with_phone_number() {
    Recipient recipient = givenPhoneRecipient(ProfileName.fromParts("Alice", null), "+1555123456");

    Intent intent = RecipientExporter.export(recipient).asAddContactIntent();

    assertEquals(Intent.ACTION_INSERT_OR_EDIT, intent.getAction());
    assertEquals(ContactsContract.Contacts.CONTENT_ITEM_TYPE, intent.getType());
    assertEquals("Alice", intent.getStringExtra(NAME));
    assertEquals("+1555123456", intent.getStringExtra(PHONE));
    assertNull(intent.getStringExtra(EMAIL));
  }

  @Test
  public void asAddContactIntent_with_email() {
    Recipient recipient = givenEmailRecipient(ProfileName.fromParts("Bob", null), "bob@signal.org");

    Intent intent = RecipientExporter.export(recipient).asAddContactIntent();

    assertEquals(Intent.ACTION_INSERT_OR_EDIT, intent.getAction());
    assertEquals(ContactsContract.Contacts.CONTENT_ITEM_TYPE, intent.getType());
    assertEquals("Bob", intent.getStringExtra(NAME));
    assertEquals("bob@signal.org", intent.getStringExtra(EMAIL));
    assertNull(intent.getStringExtra(PHONE));
  }

  // add profile sharing test, default is false
  @Test
  public void isProfileSharing_test() {
    Recipient recipient = givenEmailRecipient(ProfileName.fromParts("Bob", null), "bob@signal.org");
    assertEquals(false, recipient.isProfileSharing() );

  }


  private Recipient givenPhoneRecipient(ProfileName profileName, String phone) {
    Recipient recipient = mock(Recipient.class);
    when(recipient.getProfileName()).thenReturn(profileName);

    when(recipient.requireE164()).thenReturn(phone);
    when(recipient.getE164()).thenAnswer(i -> Optional.of(phone));
    when(recipient.getEmail()).thenAnswer(i -> Optional.absent());

    return recipient;
  }

  private Recipient givenEmailRecipient(ProfileName profileName, String email) {
    Recipient recipient = mock(Recipient.class);
    when(recipient.getProfileName()).thenReturn(profileName);

    when(recipient.requireEmail()).thenReturn(email);
    when(recipient.getEmail()).thenAnswer(i -> Optional.of(email));
    when(recipient.getE164()).thenAnswer(i -> Optional.absent());

    return recipient;
  }

}
