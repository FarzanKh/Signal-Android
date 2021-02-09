package org.whispersystems.signalservice.api.groupsv2;

import com.google.protobuf.ByteString;

import org.junit.Test;
import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedString;
import org.signal.storageservice.protos.groups.local.DecryptedTimer;
import org.signal.zkgroup.profiles.ProfileKey;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.util.Util;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.admin;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.approveAdmin;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.approveMember;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.demoteAdmin;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.member;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.newProfileKey;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.pendingMember;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.pendingMemberRemoval;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.promoteAdmin;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.randomProfileKey;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.requestingMember;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.withProfileKey;
import static org.whispersystems.signalservice.api.groupsv2.ProtobufTestUtils.getMaxDeclaredFieldNumber;

public final class GroupChangeReconstructTest {

  /**
   * Reflects over the generated protobuf class and ensures that no new fields have been added since we wrote this.
   * <p>
   * If we didn't, newly added fields would not be detected by {@link GroupChangeReconstruct#reconstructGroupChange}.
   */
  @Test
  public void ensure_GroupChangeReconstruct_knows_about_all_fields_of_DecryptedGroup() {
    int maxFieldFound = getMaxDeclaredFieldNumber(DecryptedGroup.class);

    assertEquals("GroupChangeReconstruct and its tests need updating to account for new fields on " + DecryptedGroup.class.getName(),
                 10, maxFieldFound);
  }

  @Test
  public void empty_to_empty() {
    DecryptedGroup from = DecryptedGroup.newBuilder().build();
    DecryptedGroup to   = DecryptedGroup.newBuilder().build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().build(), decryptedGroupChange);
  }

  @Test
  public void revision_set_to_the_target() {
    DecryptedGroup from = DecryptedGroup.newBuilder().setRevision(10).build();
    DecryptedGroup to   = DecryptedGroup.newBuilder().setRevision(20).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(20, decryptedGroupChange.getRevision());
  }

  @Test
  public void title_change() {
    DecryptedGroup from = DecryptedGroup.newBuilder().setTitle("A").build();
    DecryptedGroup to   = DecryptedGroup.newBuilder().setTitle("B").build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().setNewTitle(DecryptedString.newBuilder().setValue("B")).build(), decryptedGroupChange);
  }

  @Test
  public void avatar_change() {
    DecryptedGroup from = DecryptedGroup.newBuilder().setAvatar("A").build();
    DecryptedGroup to   = DecryptedGroup.newBuilder().setAvatar("B").build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().setNewAvatar(DecryptedString.newBuilder().setValue("B")).build(), decryptedGroupChange);
  }

  @Test
  public void timer_change() {
    DecryptedGroup from = DecryptedGroup.newBuilder().setDisappearingMessagesTimer(DecryptedTimer.newBuilder().setDuration(100)).build();
    DecryptedGroup to   = DecryptedGroup.newBuilder().setDisappearingMessagesTimer(DecryptedTimer.newBuilder().setDuration(200)).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().setNewTimer(DecryptedTimer.newBuilder().setDuration(200)).build(), decryptedGroupChange);
  }

  @Test
  public void access_control_change_attributes() {
    DecryptedGroup from = DecryptedGroup.newBuilder().setAccessControl(AccessControl.newBuilder().setAttributes(AccessControl.AccessRequired.MEMBER)).build();
    DecryptedGroup to   = DecryptedGroup.newBuilder().setAccessControl(AccessControl.newBuilder().setAttributes(AccessControl.AccessRequired.ADMINISTRATOR)).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().setNewAttributeAccess(AccessControl.AccessRequired.ADMINISTRATOR).build(), decryptedGroupChange);
  }

  @Test
  public void access_control_change_membership() {
    DecryptedGroup from = DecryptedGroup.newBuilder().setAccessControl(AccessControl.newBuilder().setMembers(AccessControl.AccessRequired.ADMINISTRATOR)).build();
    DecryptedGroup to   = DecryptedGroup.newBuilder().setAccessControl(AccessControl.newBuilder().setMembers(AccessControl.AccessRequired.MEMBER)).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().setNewMemberAccess(AccessControl.AccessRequired.MEMBER).build(), decryptedGroupChange);
  }

  @Test
  public void access_control_change_membership_and_attributes() {
    DecryptedGroup from = DecryptedGroup.newBuilder().setAccessControl(AccessControl.newBuilder().setMembers(AccessControl.AccessRequired.MEMBER)
                                                     .setAttributes(AccessControl.AccessRequired.ADMINISTRATOR)).build();
    DecryptedGroup to   = DecryptedGroup.newBuilder().setAccessControl(AccessControl.newBuilder().setMembers(AccessControl.AccessRequired.ADMINISTRATOR)
                                                     .setAttributes(AccessControl.AccessRequired.MEMBER)).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().setNewMemberAccess(AccessControl.AccessRequired.ADMINISTRATOR)
                   .setNewAttributeAccess(AccessControl.AccessRequired.MEMBER).build(), decryptedGroupChange);
  }

  @Test
  public void new_member() {
    UUID           uuidNew = UUID.randomUUID();
    DecryptedGroup from    = DecryptedGroup.newBuilder().build();
    DecryptedGroup to      = DecryptedGroup.newBuilder().addMembers(member(uuidNew)).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().addNewMembers(member(uuidNew)).build(), decryptedGroupChange);
  }

  @Test
  public void removed_member() {
    UUID           uuidOld = UUID.randomUUID();
    DecryptedGroup from    = DecryptedGroup.newBuilder().addMembers(member(uuidOld)).build();
    DecryptedGroup to      = DecryptedGroup.newBuilder().build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().addDeleteMembers(UuidUtil.toByteString(uuidOld)).build(), decryptedGroupChange);
  }

  @Test
  public void new_member_and_existing_member() {
    UUID           uuidOld = UUID.randomUUID();
    UUID           uuidNew = UUID.randomUUID();
    DecryptedGroup from    = DecryptedGroup.newBuilder().addMembers(member(uuidOld)).build();
    DecryptedGroup to      = DecryptedGroup.newBuilder().addMembers(member(uuidOld)).addMembers(member(uuidNew)).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().addNewMembers(member(uuidNew)).build(), decryptedGroupChange);
  }

  @Test
  public void removed_member_and_remaining_member() {
    UUID           uuidOld       = UUID.randomUUID();
    UUID           uuidRemaining = UUID.randomUUID();
    DecryptedGroup from          = DecryptedGroup.newBuilder().addMembers(member(uuidOld)).addMembers(member(uuidRemaining)).build();
    DecryptedGroup to            = DecryptedGroup.newBuilder().addMembers(member(uuidRemaining)).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().addDeleteMembers(UuidUtil.toByteString(uuidOld)).build(), decryptedGroupChange);
  }

  @Test
  public void new_member_by_invite() {
    UUID           uuidNew = UUID.randomUUID();
    DecryptedGroup from    = DecryptedGroup.newBuilder().addPendingMembers(pendingMember(uuidNew)).build();
    DecryptedGroup to      = DecryptedGroup.newBuilder().addMembers(member(uuidNew)).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().addPromotePendingMembers(member(uuidNew)).build(), decryptedGroupChange);
  }

  @Test
  public void uninvited_member_by_invite() {
    UUID           uuidNew = UUID.randomUUID();
    DecryptedGroup from    = DecryptedGroup.newBuilder().addPendingMembers(pendingMember(uuidNew)).build();
    DecryptedGroup to      = DecryptedGroup.newBuilder().build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().addDeletePendingMembers(pendingMemberRemoval(uuidNew)).build(), decryptedGroupChange);
  }

  // To see if the new member can be coreectly added to a group with two members.
  @Test
  public void new_member_2() {
    UUID           uuid1 = UUID.randomUUID();
    UUID           uuid2 = UUID.randomUUID();
    UUID           uuid3 = UUID.randomUUID();

    DecryptedGroup from    = DecryptedGroup.newBuilder().addMembers(member(uuid1)).addMembers(member(uuid2)).build();
    DecryptedGroup to      = DecryptedGroup.newBuilder().addMembers(member(uuid1)).addMembers(member(uuid2)).addMembers(member(uuid3)).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().addNewMembers(member(uuid3)).build(), decryptedGroupChange);
  }

  // To see if the old member can be added to a group with two members.
  @Test
  public void old_member_2() {
    UUID           uuid1 = UUID.randomUUID();
    UUID           uuid2 = UUID.randomUUID();
    DecryptedGroup from    = DecryptedGroup.newBuilder().addMembers(member(uuid1)).addMembers(member(uuid2)).build();
    DecryptedGroup to      = DecryptedGroup.newBuilder().addMembers(member(uuid1)).addMembers(member(uuid2)).addMembers(member(uuid2)).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().build(), decryptedGroupChange);
  }

  // To see if the member can be correctly removed from a group with two members
  @Test
  public void removed_member_2() {
    UUID           uuid1 = UUID.randomUUID();
    UUID           uuid2 = UUID.randomUUID();
    DecryptedGroup from    = DecryptedGroup.newBuilder().addMembers(member(uuid1)).addMembers(member(uuid2)).build();
    DecryptedGroup to      = DecryptedGroup.newBuilder().addMembers(member(uuid1)).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().addDeleteMembers(UuidUtil.toByteString(uuid2)).build(), decryptedGroupChange);
  }

  // To see if the invited member can be correctly invited to a group with two members
  @Test
  public void new_member_by_invite_2() {
    UUID           uuid1 = UUID.randomUUID();
    UUID           uuid2 = UUID.randomUUID();
    DecryptedGroup from    = DecryptedGroup.newBuilder().addMembers(member(uuid1)).addPendingMembers(pendingMember(uuid2)).build();
    DecryptedGroup to      = DecryptedGroup.newBuilder().addMembers(member(uuid1)).addMembers(member(uuid2)).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().addPromotePendingMembers(member(uuid2)).build(), decryptedGroupChange);
  }

  // To see if the uninvited member can be invited to a group with two members
  @Test
  public void uninvited_member_by_invite_2() {
    UUID           uuid1 = UUID.randomUUID();
    UUID           uuid2 = UUID.randomUUID();
    DecryptedGroup from    = DecryptedGroup.newBuilder().addMembers(member(uuid1)).addPendingMembers(pendingMember(uuid2)).build();
    DecryptedGroup to      = DecryptedGroup.newBuilder().addMembers(member(uuid1)).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().addDeletePendingMembers(pendingMemberRemoval(uuid2)).build(), decryptedGroupChange);
  }

  // To see if the new member can be coreectly added to a group with three members.
  @Test
  public void new_member_3() {
    UUID           uuid1 = UUID.randomUUID();
    UUID           uuid2 = UUID.randomUUID();
    UUID           uuid3 = UUID.randomUUID();
    UUID           uuid4 = UUID.randomUUID();


    DecryptedGroup from    = DecryptedGroup.newBuilder().addMembers(member(uuid1)).addMembers(member(uuid2)).addMembers(member(uuid3)).build();
    DecryptedGroup to      = DecryptedGroup.newBuilder().addMembers(member(uuid1)).addMembers(member(uuid2)).addMembers(member(uuid3)).addMembers(member(uuid4)).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().addNewMembers(member(uuid4)).build(), decryptedGroupChange);
  }

  // To see if the old member can be added to a group with three members.
  @Test
  public void old_member_3() {
    UUID           uuid1 = UUID.randomUUID();
    UUID           uuid2 = UUID.randomUUID();
    UUID           uuid3 = UUID.randomUUID();
    DecryptedGroup from    = DecryptedGroup.newBuilder().addMembers(member(uuid1)).addMembers(member(uuid2)).addMembers(member(uuid3)).build();
    DecryptedGroup to      = DecryptedGroup.newBuilder().addMembers(member(uuid1)).addMembers(member(uuid2)).addMembers(member(uuid3)).addMembers(member(uuid3)).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().build(), decryptedGroupChange);
  }

  // To see if the member can be correctly removed from a group with three members
  @Test
  public void removed_member_3() {
    UUID           uuid1 = UUID.randomUUID();
    UUID           uuid2 = UUID.randomUUID();
    UUID           uuid3 = UUID.randomUUID();
    DecryptedGroup from    = DecryptedGroup.newBuilder().addMembers(member(uuid1)).addMembers(member(uuid2)).addMembers(member(uuid3)).build();
    DecryptedGroup to      = DecryptedGroup.newBuilder().addMembers(member(uuid1)).addMembers(member(uuid2)).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().addDeleteMembers(UuidUtil.toByteString(uuid3)).build(), decryptedGroupChange);
  }

  // To see if the invited member can be correctly invited to a group with three members
  @Test
  public void new_member_by_invite_3() {
    UUID           uuid1 = UUID.randomUUID();
    UUID           uuid2 = UUID.randomUUID();
    UUID           uuid3 = UUID.randomUUID();
    DecryptedGroup from    = DecryptedGroup.newBuilder().addMembers(member(uuid1)).addMembers(member(uuid2)).addPendingMembers(pendingMember(uuid3)).build();
    DecryptedGroup to      = DecryptedGroup.newBuilder().addMembers(member(uuid1)).addMembers(member(uuid2)).addMembers(member(uuid3)).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().addPromotePendingMembers(member(uuid3)).build(), decryptedGroupChange);
  }

  // To see if the uninvited member can be invited to a group with three members
  @Test
  public void uninvited_member_by_invite_3() {
    UUID           uuid1 = UUID.randomUUID();
    UUID           uuid2 = UUID.randomUUID();
    UUID           uuid3 = UUID.randomUUID();
    DecryptedGroup from    = DecryptedGroup.newBuilder().addMembers(member(uuid1)).addMembers(member(uuid2)).addPendingMembers(pendingMember(uuid3)).build();
    DecryptedGroup to      = DecryptedGroup.newBuilder().addMembers(member(uuid1)).addMembers(member(uuid2)).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().addDeletePendingMembers(pendingMemberRemoval(uuid3)).build(), decryptedGroupChange);
  }

  // To see if the new member can be coreectly added to a group with four members.
  @Test
  public void new_member_4() {
    UUID           uuid1 = UUID.randomUUID();
    UUID           uuid2 = UUID.randomUUID();
    UUID           uuid3 = UUID.randomUUID();
    UUID           uuid4 = UUID.randomUUID();
    UUID           uuid5 = UUID.randomUUID();
    DecryptedGroup from    = DecryptedGroup.newBuilder().addMembers(member(uuid1)).addMembers(member(uuid2)).addMembers(member(uuid3)).addMembers(member(uuid4)).build();
    DecryptedGroup to      = DecryptedGroup.newBuilder().addMembers(member(uuid1)).addMembers(member(uuid2)).addMembers(member(uuid3)).addMembers(member(uuid4)).addMembers(member(uuid5)).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().addNewMembers(member(uuid5)).build(), decryptedGroupChange);
  }

  // To see if the old member can be added to a group with four members.
  @Test
  public void old_member_4() {
    UUID           uuid1 = UUID.randomUUID();
    UUID           uuid2 = UUID.randomUUID();
    UUID           uuid3 = UUID.randomUUID();
    UUID           uuid4 = UUID.randomUUID();
    DecryptedGroup from    = DecryptedGroup.newBuilder().addMembers(member(uuid1)).addMembers(member(uuid2)).addMembers(member(uuid3)).addMembers(member(uuid4)).build();
    DecryptedGroup to      = DecryptedGroup.newBuilder().addMembers(member(uuid1)).addMembers(member(uuid2)).addMembers(member(uuid3)).addMembers(member(uuid4)).addMembers(member(uuid4)).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().build(), decryptedGroupChange);
  }

  // To see if the member can be correctly removed from a group with four members
  @Test
  public void removed_member_4() {
    UUID           uuid1 = UUID.randomUUID();
    UUID           uuid2 = UUID.randomUUID();
    UUID           uuid3 = UUID.randomUUID();
    UUID           uuid4 = UUID.randomUUID();
    DecryptedGroup from    = DecryptedGroup.newBuilder().addMembers(member(uuid1)).addMembers(member(uuid2)).addMembers(member(uuid3)).addMembers(member(uuid4)).build();
    DecryptedGroup to      = DecryptedGroup.newBuilder().addMembers(member(uuid1)).addMembers(member(uuid2)).addMembers(member(uuid3)).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().addDeleteMembers(UuidUtil.toByteString(uuid4)).build(), decryptedGroupChange);
  }

  // To see if the invited member can be correctly invited to a group with four members
  @Test
  public void new_member_by_invite_4() {
    UUID           uuid1 = UUID.randomUUID();
    UUID           uuid2 = UUID.randomUUID();
    UUID           uuid3 = UUID.randomUUID();
    UUID           uuid4 = UUID.randomUUID();

    DecryptedGroup from    = DecryptedGroup.newBuilder().addMembers(member(uuid1)).addMembers(member(uuid2)).addMembers(member(uuid3)).addPendingMembers(pendingMember(uuid4)).build();
    DecryptedGroup to      = DecryptedGroup.newBuilder().addMembers(member(uuid1)).addMembers(member(uuid2)).addMembers(member(uuid3)).addMembers(member(uuid4)).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().addPromotePendingMembers(member(uuid4)).build(), decryptedGroupChange);
  }

  // To see if the uninvited member can be invited to a group with four members
  @Test
  public void uninvited_member_by_invite_4() {
    UUID           uuid1 = UUID.randomUUID();
    UUID           uuid2 = UUID.randomUUID();
    UUID           uuid3 = UUID.randomUUID();
    UUID           uuid4 = UUID.randomUUID();

    DecryptedGroup from    = DecryptedGroup.newBuilder().addMembers(member(uuid1)).addMembers(member(uuid2)).addMembers(member(uuid3)).addPendingMembers(pendingMember(uuid4)).build();
    DecryptedGroup to      = DecryptedGroup.newBuilder().addMembers(member(uuid1)).addMembers(member(uuid2)).addMembers(member(uuid3)).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().addDeletePendingMembers(pendingMemberRemoval(uuid4)).build(), decryptedGroupChange);
  }

  // To see if the new member can be coreectly added to a group with 150 members.
  @Test
  public void new_member_hugeGroup() {
    List<UUID> set = new ArrayList<UUID>();  // import
    while (set.size() < 151) {
      UUID           uuid = UUID.randomUUID();
      if (!set.contains(uuid)) {
        set.add(uuid);
      }
    }

        DecryptedGroup from    = DecryptedGroup.newBuilder()
                .addMembers(member(set.get(0))).addMembers(member(set.get(1))).addMembers(member(set.get(2))).addMembers(member(set.get(3))).addMembers(member(set.get(4))).addMembers(member(set.get(5))).addMembers(member(set.get(6)))
                .addMembers(member(set.get(7))).addMembers(member(set.get(8))).addMembers(member(set.get(9))).addMembers(member(set.get(10))).
                        addMembers(member(set.get(11))).addMembers(member(set.get(12))).addMembers(member(set.get(13))).addMembers(member(set.get(14))).addMembers(member(set.get(15))).addMembers(member(set.get(16))).addMembers(member(set.get(17)))
                .addMembers(member(set.get(18))).addMembers(member(set.get(19))).addMembers(member(set.get(20))).
                        addMembers(member(set.get(21))).addMembers(member(set.get(22))).addMembers(member(set.get(23))).addMembers(member(set.get(24))).addMembers(member(set.get(25))).addMembers(member(set.get(26))).addMembers(member(set.get(27)))
                .addMembers(member(set.get(28))).addMembers(member(set.get(29))).addMembers(member(set.get(30))).
                        addMembers(member(set.get(31))).addMembers(member(set.get(32))).addMembers(member(set.get(33))).addMembers(member(set.get(34))).addMembers(member(set.get(35))).addMembers(member(set.get(36))).addMembers(member(set.get(37)))
                .addMembers(member(set.get(38))).addMembers(member(set.get(39))).addMembers(member(set.get(40))).
                        addMembers(member(set.get(41))).addMembers(member(set.get(42))).addMembers(member(set.get(43))).addMembers(member(set.get(44))).addMembers(member(set.get(45))).addMembers(member(set.get(46))).addMembers(member(set.get(47)))
                .addMembers(member(set.get(48))).addMembers(member(set.get(49))).addMembers(member(set.get(50))).
                        addMembers(member(set.get(51))).addMembers(member(set.get(52))).addMembers(member(set.get(53))).addMembers(member(set.get(54))).addMembers(member(set.get(55))).addMembers(member(set.get(56))).addMembers(member(set.get(57)))
                .addMembers(member(set.get(58))).addMembers(member(set.get(59))).addMembers(member(set.get(60))).
                        addMembers(member(set.get(61))).addMembers(member(set.get(62))).addMembers(member(set.get(63))).addMembers(member(set.get(64))).addMembers(member(set.get(65))).addMembers(member(set.get(66))).addMembers(member(set.get(67)))
                .addMembers(member(set.get(68))).addMembers(member(set.get(69))).addMembers(member(set.get(70))).
                        addMembers(member(set.get(71))).addMembers(member(set.get(72))).addMembers(member(set.get(73))).addMembers(member(set.get(74))).addMembers(member(set.get(75))).addMembers(member(set.get(76))).addMembers(member(set.get(77)))
                .addMembers(member(set.get(78))).addMembers(member(set.get(79))).addMembers(member(set.get(80))).
                        addMembers(member(set.get(81))).addMembers(member(set.get(82))).addMembers(member(set.get(83))).addMembers(member(set.get(84))).addMembers(member(set.get(85))).addMembers(member(set.get(86))).addMembers(member(set.get(87)))
                .addMembers(member(set.get(88))).addMembers(member(set.get(89))).addMembers(member(set.get(90))).
                        addMembers(member(set.get(91))).addMembers(member(set.get(92))).addMembers(member(set.get(93))).addMembers(member(set.get(94))).addMembers(member(set.get(95))).addMembers(member(set.get(96))).addMembers(member(set.get(97)))
                .addMembers(member(set.get(98))).addMembers(member(set.get(99))).addMembers(member(set.get(100))).
                        addMembers(member(set.get(101))).addMembers(member(set.get(102))).addMembers(member(set.get(103))).addMembers(member(set.get(104))).addMembers(member(set.get(105))).addMembers(member(set.get(106))).addMembers(member(set.get(107)))
                .addMembers(member(set.get(108))).addMembers(member(set.get(109))).addMembers(member(set.get(110))).
                        addMembers(member(set.get(111))).addMembers(member(set.get(112))).addMembers(member(set.get(113))).addMembers(member(set.get(114))).addMembers(member(set.get(115))).addMembers(member(set.get(116))).addMembers(member(set.get(117)))
                .addMembers(member(set.get(118))).addMembers(member(set.get(119))).addMembers(member(set.get(120))).
                        addMembers(member(set.get(121))).addMembers(member(set.get(122))).addMembers(member(set.get(123))).addMembers(member(set.get(124))).addMembers(member(set.get(125))).addMembers(member(set.get(126))).addMembers(member(set.get(127)))
                .addMembers(member(set.get(128))).addMembers(member(set.get(129))).addMembers(member(set.get(130))).
                        addMembers(member(set.get(131))).addMembers(member(set.get(132))).addMembers(member(set.get(133))).addMembers(member(set.get(134))).addMembers(member(set.get(135))).addMembers(member(set.get(136))).addMembers(member(set.get(137)))
                .addMembers(member(set.get(138))).addMembers(member(set.get(139))).addMembers(member(set.get(140))).
                        addMembers(member(set.get(141))).addMembers(member(set.get(142))).addMembers(member(set.get(143))).addMembers(member(set.get(144))).addMembers(member(set.get(145))).addMembers(member(set.get(146))).addMembers(member(set.get(147)))
                .addMembers(member(set.get(148))).addMembers(member(set.get(149))).build();

    DecryptedGroup to     = DecryptedGroup.newBuilder()
            .addMembers(member(set.get(0))).addMembers(member(set.get(1))).addMembers(member(set.get(2))).addMembers(member(set.get(3))).addMembers(member(set.get(4))).addMembers(member(set.get(5))).addMembers(member(set.get(6)))
            .addMembers(member(set.get(7))).addMembers(member(set.get(8))).addMembers(member(set.get(9))).addMembers(member(set.get(10))).
                    addMembers(member(set.get(11))).addMembers(member(set.get(12))).addMembers(member(set.get(13))).addMembers(member(set.get(14))).addMembers(member(set.get(15))).addMembers(member(set.get(16))).addMembers(member(set.get(17)))
            .addMembers(member(set.get(18))).addMembers(member(set.get(19))).addMembers(member(set.get(20))).
                    addMembers(member(set.get(21))).addMembers(member(set.get(22))).addMembers(member(set.get(23))).addMembers(member(set.get(24))).addMembers(member(set.get(25))).addMembers(member(set.get(26))).addMembers(member(set.get(27)))
            .addMembers(member(set.get(28))).addMembers(member(set.get(29))).addMembers(member(set.get(30))).
                    addMembers(member(set.get(31))).addMembers(member(set.get(32))).addMembers(member(set.get(33))).addMembers(member(set.get(34))).addMembers(member(set.get(35))).addMembers(member(set.get(36))).addMembers(member(set.get(37)))
            .addMembers(member(set.get(38))).addMembers(member(set.get(39))).addMembers(member(set.get(40))).
                    addMembers(member(set.get(41))).addMembers(member(set.get(42))).addMembers(member(set.get(43))).addMembers(member(set.get(44))).addMembers(member(set.get(45))).addMembers(member(set.get(46))).addMembers(member(set.get(47)))
            .addMembers(member(set.get(48))).addMembers(member(set.get(49))).addMembers(member(set.get(50))).
                    addMembers(member(set.get(51))).addMembers(member(set.get(52))).addMembers(member(set.get(53))).addMembers(member(set.get(54))).addMembers(member(set.get(55))).addMembers(member(set.get(56))).addMembers(member(set.get(57)))
            .addMembers(member(set.get(58))).addMembers(member(set.get(59))).addMembers(member(set.get(60))).
                    addMembers(member(set.get(61))).addMembers(member(set.get(62))).addMembers(member(set.get(63))).addMembers(member(set.get(64))).addMembers(member(set.get(65))).addMembers(member(set.get(66))).addMembers(member(set.get(67)))
            .addMembers(member(set.get(68))).addMembers(member(set.get(69))).addMembers(member(set.get(70))).
                    addMembers(member(set.get(71))).addMembers(member(set.get(72))).addMembers(member(set.get(73))).addMembers(member(set.get(74))).addMembers(member(set.get(75))).addMembers(member(set.get(76))).addMembers(member(set.get(77)))
            .addMembers(member(set.get(78))).addMembers(member(set.get(79))).addMembers(member(set.get(80))).
                    addMembers(member(set.get(81))).addMembers(member(set.get(82))).addMembers(member(set.get(83))).addMembers(member(set.get(84))).addMembers(member(set.get(85))).addMembers(member(set.get(86))).addMembers(member(set.get(87)))
            .addMembers(member(set.get(88))).addMembers(member(set.get(89))).addMembers(member(set.get(90))).
                    addMembers(member(set.get(91))).addMembers(member(set.get(92))).addMembers(member(set.get(93))).addMembers(member(set.get(94))).addMembers(member(set.get(95))).addMembers(member(set.get(96))).addMembers(member(set.get(97)))
            .addMembers(member(set.get(98))).addMembers(member(set.get(99))).addMembers(member(set.get(100))).
                    addMembers(member(set.get(101))).addMembers(member(set.get(102))).addMembers(member(set.get(103))).addMembers(member(set.get(104))).addMembers(member(set.get(105))).addMembers(member(set.get(106))).addMembers(member(set.get(107)))
            .addMembers(member(set.get(108))).addMembers(member(set.get(109))).addMembers(member(set.get(110))).
                    addMembers(member(set.get(111))).addMembers(member(set.get(112))).addMembers(member(set.get(113))).addMembers(member(set.get(114))).addMembers(member(set.get(115))).addMembers(member(set.get(116))).addMembers(member(set.get(117)))
            .addMembers(member(set.get(118))).addMembers(member(set.get(119))).addMembers(member(set.get(120))).
                    addMembers(member(set.get(121))).addMembers(member(set.get(122))).addMembers(member(set.get(123))).addMembers(member(set.get(124))).addMembers(member(set.get(125))).addMembers(member(set.get(126))).addMembers(member(set.get(127)))
            .addMembers(member(set.get(128))).addMembers(member(set.get(129))).addMembers(member(set.get(130))).
                    addMembers(member(set.get(131))).addMembers(member(set.get(132))).addMembers(member(set.get(133))).addMembers(member(set.get(134))).addMembers(member(set.get(135))).addMembers(member(set.get(136))).addMembers(member(set.get(137)))
            .addMembers(member(set.get(138))).addMembers(member(set.get(139))).addMembers(member(set.get(140))).
                    addMembers(member(set.get(141))).addMembers(member(set.get(142))).addMembers(member(set.get(143))).addMembers(member(set.get(144))).addMembers(member(set.get(145))).addMembers(member(set.get(146))).addMembers(member(set.get(147)))
            .addMembers(member(set.get(148))).addMembers(member(set.get(149))).addMembers(member(set.get(150))).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().addNewMembers(member(set.get(150))).build(), decryptedGroupChange);
  }

  @Test
  public void new_member_hugeGroup1000() {
    List<UUID> set = new ArrayList<UUID>();  // import
    while (set.size() < 1000) {
      UUID           uuid = UUID.randomUUID();
      if (!set.contains(uuid)) {
        set.add(uuid);
      }
    }

        DecryptedGroup from    = DecryptedGroup.newBuilder()
                .addMembers(member(set.get(0))).addMembers(member(set.get(1))).addMembers(member(set.get(2))).addMembers(member(set.get(3))).addMembers(member(set.get(4))).addMembers(member(set.get(5))).addMembers(member(set.get(6)))
                .addMembers(member(set.get(7))).addMembers(member(set.get(8))).addMembers(member(set.get(9))).addMembers(member(set.get(10))).
                        addMembers(member(set.get(11))).addMembers(member(set.get(12))).addMembers(member(set.get(13))).addMembers(member(set.get(14))).addMembers(member(set.get(15))).addMembers(member(set.get(16))).addMembers(member(set.get(17)))
                .addMembers(member(set.get(18))).addMembers(member(set.get(19))).addMembers(member(set.get(20))).
                        addMembers(member(set.get(21))).addMembers(member(set.get(22))).addMembers(member(set.get(23))).addMembers(member(set.get(24))).addMembers(member(set.get(25))).addMembers(member(set.get(26))).addMembers(member(set.get(27)))
                .addMembers(member(set.get(28))).addMembers(member(set.get(29))).addMembers(member(set.get(30))).
                        addMembers(member(set.get(31))).addMembers(member(set.get(32))).addMembers(member(set.get(33))).addMembers(member(set.get(34))).addMembers(member(set.get(35))).addMembers(member(set.get(36))).addMembers(member(set.get(37)))
                .addMembers(member(set.get(38))).addMembers(member(set.get(39))).addMembers(member(set.get(40))).
                        addMembers(member(set.get(41))).addMembers(member(set.get(42))).addMembers(member(set.get(43))).addMembers(member(set.get(44))).addMembers(member(set.get(45))).addMembers(member(set.get(46))).addMembers(member(set.get(47)))
                .addMembers(member(set.get(48))).addMembers(member(set.get(49))).addMembers(member(set.get(50))).
                        addMembers(member(set.get(51))).addMembers(member(set.get(52))).addMembers(member(set.get(53))).addMembers(member(set.get(54))).addMembers(member(set.get(55))).addMembers(member(set.get(56))).addMembers(member(set.get(57)))
                .addMembers(member(set.get(58))).addMembers(member(set.get(59))).addMembers(member(set.get(60))).
                        addMembers(member(set.get(61))).addMembers(member(set.get(62))).addMembers(member(set.get(63))).addMembers(member(set.get(64))).addMembers(member(set.get(65))).addMembers(member(set.get(66))).addMembers(member(set.get(67)))
                .addMembers(member(set.get(68))).addMembers(member(set.get(69))).addMembers(member(set.get(70))).
                        addMembers(member(set.get(71))).addMembers(member(set.get(72))).addMembers(member(set.get(73))).addMembers(member(set.get(74))).addMembers(member(set.get(75))).addMembers(member(set.get(76))).addMembers(member(set.get(77)))
                .addMembers(member(set.get(78))).addMembers(member(set.get(79))).addMembers(member(set.get(80))).
                        addMembers(member(set.get(81))).addMembers(member(set.get(82))).addMembers(member(set.get(83))).addMembers(member(set.get(84))).addMembers(member(set.get(85))).addMembers(member(set.get(86))).addMembers(member(set.get(87)))
                .addMembers(member(set.get(88))).addMembers(member(set.get(89))).addMembers(member(set.get(90))).
                        addMembers(member(set.get(91))).addMembers(member(set.get(92))).addMembers(member(set.get(93))).addMembers(member(set.get(94))).addMembers(member(set.get(95))).addMembers(member(set.get(96))).addMembers(member(set.get(97)))
                .addMembers(member(set.get(98))).addMembers(member(set.get(99))).addMembers(member(set.get(100))).
                        addMembers(member(set.get(101))).addMembers(member(set.get(102))).addMembers(member(set.get(103))).addMembers(member(set.get(104))).addMembers(member(set.get(105))).addMembers(member(set.get(106))).addMembers(member(set.get(107)))
                .addMembers(member(set.get(108))).addMembers(member(set.get(109))).addMembers(member(set.get(110))).
                        addMembers(member(set.get(111))).addMembers(member(set.get(112))).addMembers(member(set.get(113))).addMembers(member(set.get(114))).addMembers(member(set.get(115))).addMembers(member(set.get(116))).addMembers(member(set.get(117)))
                .addMembers(member(set.get(118))).addMembers(member(set.get(119))).addMembers(member(set.get(120))).
                        addMembers(member(set.get(121))).addMembers(member(set.get(122))).addMembers(member(set.get(123))).addMembers(member(set.get(124))).addMembers(member(set.get(125))).addMembers(member(set.get(126))).addMembers(member(set.get(127)))
                .addMembers(member(set.get(128))).addMembers(member(set.get(129))).addMembers(member(set.get(130))).
                        addMembers(member(set.get(131))).addMembers(member(set.get(132))).addMembers(member(set.get(133))).addMembers(member(set.get(134))).addMembers(member(set.get(135))).addMembers(member(set.get(136))).addMembers(member(set.get(137)))
                .addMembers(member(set.get(138))).addMembers(member(set.get(139))).addMembers(member(set.get(140))).
                        addMembers(member(set.get(141))).addMembers(member(set.get(142))).addMembers(member(set.get(143))).addMembers(member(set.get(144))).addMembers(member(set.get(145))).addMembers(member(set.get(146))).addMembers(member(set.get(147))).addMembers(member(set.get(148)))
                .addMembers(member(set.get(149))).addMembers(member(set.get(150))).addMembers(member(set.get(151))).addMembers(member(set.get(152))).addMembers(member(set.get(153))).addMembers(member(set.get(154))).addMembers(member(set.get(155))).addMembers(member(set.get(156))).addMembers(member(set.get(157))).addMembers(member(set.get(158))).addMembers(member(set.get(159))).addMembers(member(set.get(160))).addMembers(member(set.get(161))).addMembers(member(set.get(162))).addMembers(member(set.get(163))).addMembers(member(set.get(164))).addMembers(member(set.get(165))).addMembers(member(set.get(166))).addMembers(member(set.get(167))).addMembers(member(set.get(168))).addMembers(member(set.get(169))).addMembers(member(set.get(170))).addMembers(member(set.get(171))).addMembers(member(set.get(172))).addMembers(member(set.get(173))).addMembers(member(set.get(174))).addMembers(member(set.get(175))).addMembers(member(set.get(176))).addMembers(member(set.get(177))).addMembers(member(set.get(178))).addMembers(member(set.get(179))).addMembers(member(set.get(180))).addMembers(member(set.get(181))).addMembers(member(set.get(182))).addMembers(member(set.get(183))).addMembers(member(set.get(184))).addMembers(member(set.get(185))).addMembers(member(set.get(186))).addMembers(member(set.get(187))).addMembers(member(set.get(188))).addMembers(member(set.get(189))).addMembers(member(set.get(190))).addMembers(member(set.get(191))).addMembers(member(set.get(192))).addMembers(member(set.get(193))).addMembers(member(set.get(194))).addMembers(member(set.get(195))).addMembers(member(set.get(196))).addMembers(member(set.get(197))).addMembers(member(set.get(198))).addMembers(member(set.get(199))).addMembers(member(set.get(200))).addMembers(member(set.get(201))).addMembers(member(set.get(202))).addMembers(member(set.get(203))).addMembers(member(set.get(204))).addMembers(member(set.get(205))).addMembers(member(set.get(206))).addMembers(member(set.get(207))).addMembers(member(set.get(208))).addMembers(member(set.get(209))).addMembers(member(set.get(210))).addMembers(member(set.get(211))).addMembers(member(set.get(212))).addMembers(member(set.get(213))).addMembers(member(set.get(214))).addMembers(member(set.get(215))).addMembers(member(set.get(216))).addMembers(member(set.get(217))).addMembers(member(set.get(218))).addMembers(member(set.get(219)))
                .addMembers(member(set.get(220))).addMembers(member(set.get(221))).addMembers(member(set.get(222))).addMembers(member(set.get(223))).addMembers(member(set.get(224))).addMembers(member(set.get(225))).addMembers(member(set.get(226))).addMembers(member(set.get(227))).addMembers(member(set.get(228))).addMembers(member(set.get(229))).addMembers(member(set.get(230))).addMembers(member(set.get(231))).addMembers(member(set.get(232))).addMembers(member(set.get(233))).addMembers(member(set.get(234))).addMembers(member(set.get(235))).addMembers(member(set.get(236))).addMembers(member(set.get(237))).addMembers(member(set.get(238))).addMembers(member(set.get(239))).addMembers(member(set.get(240))).addMembers(member(set.get(241))).addMembers(member(set.get(242))).addMembers(member(set.get(243))).addMembers(member(set.get(244))).addMembers(member(set.get(245))).addMembers(member(set.get(246))).addMembers(member(set.get(247))).addMembers(member(set.get(248))).addMembers(member(set.get(249))).addMembers(member(set.get(250))).addMembers(member(set.get(251))).addMembers(member(set.get(252))).addMembers(member(set.get(253))).addMembers(member(set.get(254))).addMembers(member(set.get(255))).addMembers(member(set.get(256))).addMembers(member(set.get(257))).addMembers(member(set.get(258))).addMembers(member(set.get(259))).addMembers(member(set.get(260))).addMembers(member(set.get(261))).addMembers(member(set.get(262))).addMembers(member(set.get(263))).addMembers(member(set.get(264))).addMembers(member(set.get(265))).addMembers(member(set.get(266))).addMembers(member(set.get(267))).addMembers(member(set.get(268))).addMembers(member(set.get(269))).addMembers(member(set.get(270))).addMembers(member(set.get(271))).addMembers(member(set.get(272))).addMembers(member(set.get(273))).addMembers(member(set.get(274))).addMembers(member(set.get(275))).addMembers(member(set.get(276))).addMembers(member(set.get(277))).addMembers(member(set.get(278))).addMembers(member(set.get(279))).addMembers(member(set.get(280))).addMembers(member(set.get(281))).addMembers(member(set.get(282))).addMembers(member(set.get(283))).addMembers(member(set.get(284))).addMembers(member(set.get(285))).addMembers(member(set.get(286))).addMembers(member(set.get(287))).addMembers(member(set.get(288))).addMembers(member(set.get(289))).addMembers(member(set.get(290))).addMembers(member(set.get(291)))
                .addMembers(member(set.get(292))).addMembers(member(set.get(293))).addMembers(member(set.get(294))).addMembers(member(set.get(295))).addMembers(member(set.get(296))).addMembers(member(set.get(297))).addMembers(member(set.get(298))).addMembers(member(set.get(299))).addMembers(member(set.get(300))).addMembers(member(set.get(301))).addMembers(member(set.get(302))).addMembers(member(set.get(303))).addMembers(member(set.get(304))).addMembers(member(set.get(305))).addMembers(member(set.get(306))).addMembers(member(set.get(307))).addMembers(member(set.get(308))).addMembers(member(set.get(309))).addMembers(member(set.get(310))).addMembers(member(set.get(311))).addMembers(member(set.get(312))).addMembers(member(set.get(313))).addMembers(member(set.get(314))).addMembers(member(set.get(315))).addMembers(member(set.get(316))).addMembers(member(set.get(317))).addMembers(member(set.get(318))).addMembers(member(set.get(319))).addMembers(member(set.get(320))).addMembers(member(set.get(321))).addMembers(member(set.get(322))).addMembers(member(set.get(323))).addMembers(member(set.get(324))).addMembers(member(set.get(325))).addMembers(member(set.get(326))).addMembers(member(set.get(327))).addMembers(member(set.get(328))).addMembers(member(set.get(329))).addMembers(member(set.get(330))).addMembers(member(set.get(331))).addMembers(member(set.get(332))).addMembers(member(set.get(333))).addMembers(member(set.get(334))).addMembers(member(set.get(335))).addMembers(member(set.get(336))).addMembers(member(set.get(337))).addMembers(member(set.get(338))).addMembers(member(set.get(339))).addMembers(member(set.get(340))).addMembers(member(set.get(341))).addMembers(member(set.get(342))).addMembers(member(set.get(343))).addMembers(member(set.get(344))).addMembers(member(set.get(345))).addMembers(member(set.get(346))).addMembers(member(set.get(347))).addMembers(member(set.get(348))).addMembers(member(set.get(349))).addMembers(member(set.get(350))).addMembers(member(set.get(351))).addMembers(member(set.get(352))).addMembers(member(set.get(353))).addMembers(member(set.get(354))).addMembers(member(set.get(355))).addMembers(member(set.get(356))).addMembers(member(set.get(357))).addMembers(member(set.get(358))).addMembers(member(set.get(359))).addMembers(member(set.get(360))).addMembers(member(set.get(361))).addMembers(member(set.get(362))).addMembers(member(set.get(363))).addMembers(member(set.get(364))).addMembers(member(set.get(365))).addMembers(member(set.get(366))).addMembers(member(set.get(367))).addMembers(member(set.get(368))).addMembers(member(set.get(369))).addMembers(member(set.get(370))).addMembers(member(set.get(371))).addMembers(member(set.get(372))).addMembers(member(set.get(373))).addMembers(member(set.get(374))).addMembers(member(set.get(375))).addMembers(member(set.get(376))).addMembers(member(set.get(377))).addMembers(member(set.get(378))).addMembers(member(set.get(379))).addMembers(member(set.get(380))).addMembers(member(set.get(381))).addMembers(member(set.get(382))).addMembers(member(set.get(383))).addMembers(member(set.get(384))).addMembers(member(set.get(385))).addMembers(member(set.get(386))).addMembers(member(set.get(387))).addMembers(member(set.get(388))).addMembers(member(set.get(389))).addMembers(member(set.get(390))).addMembers(member(set.get(391))).addMembers(member(set.get(392))).addMembers(member(set.get(393))).addMembers(member(set.get(394))).addMembers(member(set.get(395))).addMembers(member(set.get(396))).addMembers(member(set.get(397))).addMembers(member(set.get(398))).addMembers(member(set.get(399))).addMembers(member(set.get(400))).addMembers(member(set.get(401))).addMembers(member(set.get(402))).addMembers(member(set.get(403))).addMembers(member(set.get(404))).addMembers(member(set.get(405))).addMembers(member(set.get(406))).addMembers(member(set.get(407))).addMembers(member(set.get(408))).addMembers(member(set.get(409))).addMembers(member(set.get(410))).addMembers(member(set.get(411))).addMembers(member(set.get(412))).addMembers(member(set.get(413))).addMembers(member(set.get(414))).addMembers(member(set.get(415))).addMembers(member(set.get(416))).addMembers(member(set.get(417))).addMembers(member(set.get(418))).addMembers(member(set.get(419))).addMembers(member(set.get(420))).addMembers(member(set.get(421))).addMembers(member(set.get(422))).addMembers(member(set.get(423))).addMembers(member(set.get(424))).addMembers(member(set.get(425))).addMembers(member(set.get(426))).addMembers(member(set.get(427))).addMembers(member(set.get(428))).addMembers(member(set.get(429))).addMembers(member(set.get(430))).addMembers(member(set.get(431))).addMembers(member(set.get(432))).addMembers(member(set.get(433))).addMembers(member(set.get(434))).addMembers(member(set.get(435))).addMembers(member(set.get(436))).addMembers(member(set.get(437))).addMembers(member(set.get(438))).addMembers(member(set.get(439))).addMembers(member(set.get(440))).addMembers(member(set.get(441))).addMembers(member(set.get(442))).addMembers(member(set.get(443))).addMembers(member(set.get(444))).addMembers(member(set.get(445))).addMembers(member(set.get(446))).addMembers(member(set.get(447))).addMembers(member(set.get(448))).addMembers(member(set.get(449))).addMembers(member(set.get(450))).addMembers(member(set.get(451))).addMembers(member(set.get(452))).addMembers(member(set.get(453))).addMembers(member(set.get(454))).addMembers(member(set.get(455))).addMembers(member(set.get(456))).addMembers(member(set.get(457))).addMembers(member(set.get(458))).addMembers(member(set.get(459))).addMembers(member(set.get(460))).addMembers(member(set.get(461))).addMembers(member(set.get(462))).addMembers(member(set.get(463))).addMembers(member(set.get(464))).addMembers(member(set.get(465)))
                .addMembers(member(set.get(466))).addMembers(member(set.get(467))).addMembers(member(set.get(468))).addMembers(member(set.get(469))).addMembers(member(set.get(470))).addMembers(member(set.get(471))).addMembers(member(set.get(472))).addMembers(member(set.get(473))).addMembers(member(set.get(474))).addMembers(member(set.get(475))).addMembers(member(set.get(476))).addMembers(member(set.get(477))).addMembers(member(set.get(478))).addMembers(member(set.get(479))).addMembers(member(set.get(480))).addMembers(member(set.get(481))).addMembers(member(set.get(482))).addMembers(member(set.get(483))).addMembers(member(set.get(484))).addMembers(member(set.get(485))).addMembers(member(set.get(486))).addMembers(member(set.get(487))).addMembers(member(set.get(488))).addMembers(member(set.get(489))).addMembers(member(set.get(490))).addMembers(member(set.get(491))).addMembers(member(set.get(492))).addMembers(member(set.get(493))).addMembers(member(set.get(494))).addMembers(member(set.get(495))).addMembers(member(set.get(496))).addMembers(member(set.get(497))).addMembers(member(set.get(498))).addMembers(member(set.get(499))).addMembers(member(set.get(500))).addMembers(member(set.get(501))).addMembers(member(set.get(502))).addMembers(member(set.get(503))).addMembers(member(set.get(504))).addMembers(member(set.get(505))).addMembers(member(set.get(506))).addMembers(member(set.get(507))).addMembers(member(set.get(508))).addMembers(member(set.get(509))).addMembers(member(set.get(510))).addMembers(member(set.get(511))).addMembers(member(set.get(512))).addMembers(member(set.get(513))).addMembers(member(set.get(514))).addMembers(member(set.get(515))).addMembers(member(set.get(516))).addMembers(member(set.get(517))).addMembers(member(set.get(518))).addMembers(member(set.get(519))).addMembers(member(set.get(520))).addMembers(member(set.get(521))).addMembers(member(set.get(522))).addMembers(member(set.get(523))).addMembers(member(set.get(524))).addMembers(member(set.get(525))).addMembers(member(set.get(526))).addMembers(member(set.get(527))).addMembers(member(set.get(528))).addMembers(member(set.get(529))).addMembers(member(set.get(530))).addMembers(member(set.get(531))).addMembers(member(set.get(532))).addMembers(member(set.get(533))).addMembers(member(set.get(534))).addMembers(member(set.get(535))).addMembers(member(set.get(536))).addMembers(member(set.get(537))).addMembers(member(set.get(538))).addMembers(member(set.get(539))).addMembers(member(set.get(540))).addMembers(member(set.get(541))).addMembers(member(set.get(542))).addMembers(member(set.get(543))).addMembers(member(set.get(544))).addMembers(member(set.get(545))).addMembers(member(set.get(546))).addMembers(member(set.get(547))).addMembers(member(set.get(548))).addMembers(member(set.get(549))).addMembers(member(set.get(550))).addMembers(member(set.get(551))).addMembers(member(set.get(552))).addMembers(member(set.get(553))).addMembers(member(set.get(554))).addMembers(member(set.get(555))).addMembers(member(set.get(556))).addMembers(member(set.get(557))).addMembers(member(set.get(558))).addMembers(member(set.get(559))).addMembers(member(set.get(560))).addMembers(member(set.get(561))).addMembers(member(set.get(562))).addMembers(member(set.get(563))).addMembers(member(set.get(564))).addMembers(member(set.get(565))).addMembers(member(set.get(566))).addMembers(member(set.get(567))).addMembers(member(set.get(568))).addMembers(member(set.get(569))).addMembers(member(set.get(570))).addMembers(member(set.get(571))).addMembers(member(set.get(572))).addMembers(member(set.get(573))).addMembers(member(set.get(574))).addMembers(member(set.get(575))).addMembers(member(set.get(576))).addMembers(member(set.get(577))).addMembers(member(set.get(578))).addMembers(member(set.get(579))).addMembers(member(set.get(580))).addMembers(member(set.get(581))).addMembers(member(set.get(582))).addMembers(member(set.get(583))).addMembers(member(set.get(584))).addMembers(member(set.get(585))).addMembers(member(set.get(586))).addMembers(member(set.get(587))).addMembers(member(set.get(588))).addMembers(member(set.get(589))).addMembers(member(set.get(590))).addMembers(member(set.get(591))).addMembers(member(set.get(592))).addMembers(member(set.get(593))).addMembers(member(set.get(594))).addMembers(member(set.get(595))).addMembers(member(set.get(596))).addMembers(member(set.get(597))).addMembers(member(set.get(598))).addMembers(member(set.get(599))).addMembers(member(set.get(600))).addMembers(member(set.get(601))).addMembers(member(set.get(602))).addMembers(member(set.get(603))).addMembers(member(set.get(604))).addMembers(member(set.get(605))).addMembers(member(set.get(606))).addMembers(member(set.get(607))).addMembers(member(set.get(608))).addMembers(member(set.get(609))).addMembers(member(set.get(610))).addMembers(member(set.get(611))).addMembers(member(set.get(612))).addMembers(member(set.get(613))).addMembers(member(set.get(614))).addMembers(member(set.get(615))).addMembers(member(set.get(616))).addMembers(member(set.get(617))).addMembers(member(set.get(618))).addMembers(member(set.get(619))).addMembers(member(set.get(620))).addMembers(member(set.get(621))).addMembers(member(set.get(622))).addMembers(member(set.get(623))).addMembers(member(set.get(624))).addMembers(member(set.get(625))).addMembers(member(set.get(626))).addMembers(member(set.get(627))).addMembers(member(set.get(628))).addMembers(member(set.get(629))).addMembers(member(set.get(630))).addMembers(member(set.get(631))).addMembers(member(set.get(632))).addMembers(member(set.get(633))).addMembers(member(set.get(634))).addMembers(member(set.get(635))).addMembers(member(set.get(636))).addMembers(member(set.get(637))).addMembers(member(set.get(638))).addMembers(member(set.get(639)))
                .addMembers(member(set.get(640))).addMembers(member(set.get(641))).addMembers(member(set.get(642))).addMembers(member(set.get(643))).addMembers(member(set.get(644))).addMembers(member(set.get(645))).addMembers(member(set.get(646))).addMembers(member(set.get(647))).addMembers(member(set.get(648))).addMembers(member(set.get(649))).addMembers(member(set.get(650))).addMembers(member(set.get(651))).addMembers(member(set.get(652))).addMembers(member(set.get(653))).addMembers(member(set.get(654))).addMembers(member(set.get(655))).addMembers(member(set.get(656))).addMembers(member(set.get(657))).addMembers(member(set.get(658))).addMembers(member(set.get(659))).addMembers(member(set.get(660))).addMembers(member(set.get(661))).addMembers(member(set.get(662))).addMembers(member(set.get(663))).addMembers(member(set.get(664))).addMembers(member(set.get(665))).addMembers(member(set.get(666))).addMembers(member(set.get(667))).addMembers(member(set.get(668))).addMembers(member(set.get(669))).addMembers(member(set.get(670))).addMembers(member(set.get(671))).addMembers(member(set.get(672))).addMembers(member(set.get(673))).addMembers(member(set.get(674))).addMembers(member(set.get(675))).addMembers(member(set.get(676))).addMembers(member(set.get(677))).addMembers(member(set.get(678))).addMembers(member(set.get(679))).addMembers(member(set.get(680))).addMembers(member(set.get(681))).addMembers(member(set.get(682))).addMembers(member(set.get(683))).addMembers(member(set.get(684))).addMembers(member(set.get(685))).addMembers(member(set.get(686))).addMembers(member(set.get(687))).addMembers(member(set.get(688))).addMembers(member(set.get(689))).addMembers(member(set.get(690))).addMembers(member(set.get(691))).addMembers(member(set.get(692))).addMembers(member(set.get(693))).addMembers(member(set.get(694))).addMembers(member(set.get(695))).addMembers(member(set.get(696))).addMembers(member(set.get(697))).addMembers(member(set.get(698))).addMembers(member(set.get(699))).addMembers(member(set.get(700))).addMembers(member(set.get(701))).addMembers(member(set.get(702))).addMembers(member(set.get(703))).addMembers(member(set.get(704))).addMembers(member(set.get(705))).addMembers(member(set.get(706))).addMembers(member(set.get(707))).addMembers(member(set.get(708))).addMembers(member(set.get(709))).addMembers(member(set.get(710))).addMembers(member(set.get(711))).addMembers(member(set.get(712))).addMembers(member(set.get(713))).addMembers(member(set.get(714))).addMembers(member(set.get(715))).addMembers(member(set.get(716))).addMembers(member(set.get(717))).addMembers(member(set.get(718))).addMembers(member(set.get(719))).addMembers(member(set.get(720))).addMembers(member(set.get(721))).addMembers(member(set.get(722))).addMembers(member(set.get(723))).addMembers(member(set.get(724))).addMembers(member(set.get(725))).addMembers(member(set.get(726))).addMembers(member(set.get(727))).addMembers(member(set.get(728))).addMembers(member(set.get(729))).addMembers(member(set.get(730))).addMembers(member(set.get(731))).addMembers(member(set.get(732))).addMembers(member(set.get(733))).addMembers(member(set.get(734))).addMembers(member(set.get(735))).addMembers(member(set.get(736))).addMembers(member(set.get(737))).addMembers(member(set.get(738))).addMembers(member(set.get(739))).addMembers(member(set.get(740))).addMembers(member(set.get(741))).addMembers(member(set.get(742))).addMembers(member(set.get(743))).addMembers(member(set.get(744))).addMembers(member(set.get(745))).addMembers(member(set.get(746))).addMembers(member(set.get(747))).addMembers(member(set.get(748))).addMembers(member(set.get(749))).addMembers(member(set.get(750))).addMembers(member(set.get(751))).addMembers(member(set.get(752))).addMembers(member(set.get(753))).addMembers(member(set.get(754))).addMembers(member(set.get(755))).addMembers(member(set.get(756))).addMembers(member(set.get(757))).addMembers(member(set.get(758))).addMembers(member(set.get(759))).addMembers(member(set.get(760))).addMembers(member(set.get(761))).addMembers(member(set.get(762))).addMembers(member(set.get(763))).addMembers(member(set.get(764))).addMembers(member(set.get(765))).addMembers(member(set.get(766))).addMembers(member(set.get(767))).addMembers(member(set.get(768))).addMembers(member(set.get(769))).addMembers(member(set.get(770))).addMembers(member(set.get(771))).addMembers(member(set.get(772))).addMembers(member(set.get(773))).addMembers(member(set.get(774))).addMembers(member(set.get(775))).addMembers(member(set.get(776))).addMembers(member(set.get(777))).addMembers(member(set.get(778))).addMembers(member(set.get(779))).addMembers(member(set.get(780))).addMembers(member(set.get(781))).addMembers(member(set.get(782))).addMembers(member(set.get(783))).addMembers(member(set.get(784))).addMembers(member(set.get(785))).addMembers(member(set.get(786))).addMembers(member(set.get(787))).addMembers(member(set.get(788))).addMembers(member(set.get(789))).addMembers(member(set.get(790))).addMembers(member(set.get(791))).addMembers(member(set.get(792))).addMembers(member(set.get(793))).addMembers(member(set.get(794))).addMembers(member(set.get(795))).addMembers(member(set.get(796))).addMembers(member(set.get(797))).addMembers(member(set.get(798))).addMembers(member(set.get(799))).addMembers(member(set.get(800))).addMembers(member(set.get(801))).addMembers(member(set.get(802))).addMembers(member(set.get(803))).addMembers(member(set.get(804))).addMembers(member(set.get(805))).addMembers(member(set.get(806))).addMembers(member(set.get(807))).addMembers(member(set.get(808))).addMembers(member(set.get(809))).addMembers(member(set.get(810))).addMembers(member(set.get(811))).addMembers(member(set.get(812))).addMembers(member(set.get(813)))
                .addMembers(member(set.get(814))).addMembers(member(set.get(815))).addMembers(member(set.get(816))).addMembers(member(set.get(817))).addMembers(member(set.get(818))).addMembers(member(set.get(819))).addMembers(member(set.get(820))).addMembers(member(set.get(821))).addMembers(member(set.get(822))).addMembers(member(set.get(823))).addMembers(member(set.get(824))).addMembers(member(set.get(825))).addMembers(member(set.get(826))).addMembers(member(set.get(827))).addMembers(member(set.get(828))).addMembers(member(set.get(829))).addMembers(member(set.get(830))).addMembers(member(set.get(831))).addMembers(member(set.get(832))).addMembers(member(set.get(833))).addMembers(member(set.get(834))).addMembers(member(set.get(835))).addMembers(member(set.get(836))).addMembers(member(set.get(837))).addMembers(member(set.get(838))).addMembers(member(set.get(839))).addMembers(member(set.get(840))).addMembers(member(set.get(841))).addMembers(member(set.get(842))).addMembers(member(set.get(843))).addMembers(member(set.get(844))).addMembers(member(set.get(845))).addMembers(member(set.get(846))).addMembers(member(set.get(847))).addMembers(member(set.get(848))).addMembers(member(set.get(849))).addMembers(member(set.get(850))).addMembers(member(set.get(851))).addMembers(member(set.get(852))).addMembers(member(set.get(853))).addMembers(member(set.get(854))).addMembers(member(set.get(855))).addMembers(member(set.get(856))).addMembers(member(set.get(857))).addMembers(member(set.get(858))).addMembers(member(set.get(859))).addMembers(member(set.get(860))).addMembers(member(set.get(861))).addMembers(member(set.get(862))).addMembers(member(set.get(863))).addMembers(member(set.get(864))).addMembers(member(set.get(865))).addMembers(member(set.get(866))).addMembers(member(set.get(867))).addMembers(member(set.get(868))).addMembers(member(set.get(869))).addMembers(member(set.get(870))).addMembers(member(set.get(871))).addMembers(member(set.get(872))).addMembers(member(set.get(873))).addMembers(member(set.get(874))).addMembers(member(set.get(875))).addMembers(member(set.get(876))).addMembers(member(set.get(877))).addMembers(member(set.get(878))).addMembers(member(set.get(879))).addMembers(member(set.get(880))).addMembers(member(set.get(881))).addMembers(member(set.get(882))).addMembers(member(set.get(883))).addMembers(member(set.get(884))).addMembers(member(set.get(885))).addMembers(member(set.get(886))).addMembers(member(set.get(887))).addMembers(member(set.get(888))).addMembers(member(set.get(889))).addMembers(member(set.get(890))).addMembers(member(set.get(891))).addMembers(member(set.get(892))).addMembers(member(set.get(893))).addMembers(member(set.get(894))).addMembers(member(set.get(895))).addMembers(member(set.get(896))).addMembers(member(set.get(897))).addMembers(member(set.get(898))).addMembers(member(set.get(899))).addMembers(member(set.get(900))).addMembers(member(set.get(901))).addMembers(member(set.get(902))).addMembers(member(set.get(903))).addMembers(member(set.get(904))).addMembers(member(set.get(905))).addMembers(member(set.get(906))).addMembers(member(set.get(907))).addMembers(member(set.get(908))).addMembers(member(set.get(909))).addMembers(member(set.get(910))).addMembers(member(set.get(911))).addMembers(member(set.get(912))).addMembers(member(set.get(913))).addMembers(member(set.get(914))).addMembers(member(set.get(915))).addMembers(member(set.get(916))).addMembers(member(set.get(917))).addMembers(member(set.get(918))).addMembers(member(set.get(919))).addMembers(member(set.get(920))).addMembers(member(set.get(921))).addMembers(member(set.get(922))).addMembers(member(set.get(923))).addMembers(member(set.get(924))).addMembers(member(set.get(925))).addMembers(member(set.get(926))).addMembers(member(set.get(927))).addMembers(member(set.get(928))).addMembers(member(set.get(929))).addMembers(member(set.get(930))).addMembers(member(set.get(931))).addMembers(member(set.get(932))).addMembers(member(set.get(933))).addMembers(member(set.get(934))).addMembers(member(set.get(935))).addMembers(member(set.get(936))).addMembers(member(set.get(937))).addMembers(member(set.get(938))).addMembers(member(set.get(939))).addMembers(member(set.get(940))).addMembers(member(set.get(941))).addMembers(member(set.get(942))).addMembers(member(set.get(943))).addMembers(member(set.get(944))).addMembers(member(set.get(945))).addMembers(member(set.get(946))).addMembers(member(set.get(947))).addMembers(member(set.get(948))).addMembers(member(set.get(949))).addMembers(member(set.get(950))).addMembers(member(set.get(951))).addMembers(member(set.get(952))).addMembers(member(set.get(953))).addMembers(member(set.get(954))).addMembers(member(set.get(955))).addMembers(member(set.get(956))).addMembers(member(set.get(957))).addMembers(member(set.get(958))).addMembers(member(set.get(959))).addMembers(member(set.get(960))).addMembers(member(set.get(961))).addMembers(member(set.get(962))).addMembers(member(set.get(963))).addMembers(member(set.get(964))).addMembers(member(set.get(965))).addMembers(member(set.get(966))).addMembers(member(set.get(967))).addMembers(member(set.get(968))).addMembers(member(set.get(969))).addMembers(member(set.get(970))).addMembers(member(set.get(971))).addMembers(member(set.get(972))).addMembers(member(set.get(973))).addMembers(member(set.get(974))).addMembers(member(set.get(975))).addMembers(member(set.get(976))).addMembers(member(set.get(977))).addMembers(member(set.get(978))).addMembers(member(set.get(979))).addMembers(member(set.get(980))).addMembers(member(set.get(981))).addMembers(member(set.get(982))).addMembers(member(set.get(983))).addMembers(member(set.get(984))).addMembers(member(set.get(985))).addMembers(member(set.get(986))).addMembers(member(set.get(987)))
                .addMembers(member(set.get(988))).addMembers(member(set.get(989))).addMembers(member(set.get(990))).addMembers(member(set.get(991))).addMembers(member(set.get(992))).addMembers(member(set.get(993))).addMembers(member(set.get(994))).addMembers(member(set.get(995))).addMembers(member(set.get(996))).addMembers(member(set.get(997))).addMembers(member(set.get(998))).addMembers(member(set.get(999)))
                .build();

    DecryptedGroup to     = DecryptedGroup.newBuilder()
            .addMembers(member(set.get(0))).addMembers(member(set.get(1))).addMembers(member(set.get(2))).addMembers(member(set.get(3))).addMembers(member(set.get(4))).addMembers(member(set.get(5))).addMembers(member(set.get(6)))
            .addMembers(member(set.get(7))).addMembers(member(set.get(8))).addMembers(member(set.get(9))).addMembers(member(set.get(10))).
                    addMembers(member(set.get(11))).addMembers(member(set.get(12))).addMembers(member(set.get(13))).addMembers(member(set.get(14))).addMembers(member(set.get(15))).addMembers(member(set.get(16))).addMembers(member(set.get(17)))
            .addMembers(member(set.get(18))).addMembers(member(set.get(19))).addMembers(member(set.get(20))).
                    addMembers(member(set.get(21))).addMembers(member(set.get(22))).addMembers(member(set.get(23))).addMembers(member(set.get(24))).addMembers(member(set.get(25))).addMembers(member(set.get(26))).addMembers(member(set.get(27)))
            .addMembers(member(set.get(28))).addMembers(member(set.get(29))).addMembers(member(set.get(30))).
                    addMembers(member(set.get(31))).addMembers(member(set.get(32))).addMembers(member(set.get(33))).addMembers(member(set.get(34))).addMembers(member(set.get(35))).addMembers(member(set.get(36))).addMembers(member(set.get(37)))
            .addMembers(member(set.get(38))).addMembers(member(set.get(39))).addMembers(member(set.get(40))).
                    addMembers(member(set.get(41))).addMembers(member(set.get(42))).addMembers(member(set.get(43))).addMembers(member(set.get(44))).addMembers(member(set.get(45))).addMembers(member(set.get(46))).addMembers(member(set.get(47)))
            .addMembers(member(set.get(48))).addMembers(member(set.get(49))).addMembers(member(set.get(50))).
                    addMembers(member(set.get(51))).addMembers(member(set.get(52))).addMembers(member(set.get(53))).addMembers(member(set.get(54))).addMembers(member(set.get(55))).addMembers(member(set.get(56))).addMembers(member(set.get(57)))
            .addMembers(member(set.get(58))).addMembers(member(set.get(59))).addMembers(member(set.get(60))).
                    addMembers(member(set.get(61))).addMembers(member(set.get(62))).addMembers(member(set.get(63))).addMembers(member(set.get(64))).addMembers(member(set.get(65))).addMembers(member(set.get(66))).addMembers(member(set.get(67)))
            .addMembers(member(set.get(68))).addMembers(member(set.get(69))).addMembers(member(set.get(70))).
                    addMembers(member(set.get(71))).addMembers(member(set.get(72))).addMembers(member(set.get(73))).addMembers(member(set.get(74))).addMembers(member(set.get(75))).addMembers(member(set.get(76))).addMembers(member(set.get(77)))
            .addMembers(member(set.get(78))).addMembers(member(set.get(79))).addMembers(member(set.get(80))).
                    addMembers(member(set.get(81))).addMembers(member(set.get(82))).addMembers(member(set.get(83))).addMembers(member(set.get(84))).addMembers(member(set.get(85))).addMembers(member(set.get(86))).addMembers(member(set.get(87)))
            .addMembers(member(set.get(88))).addMembers(member(set.get(89))).addMembers(member(set.get(90))).
                    addMembers(member(set.get(91))).addMembers(member(set.get(92))).addMembers(member(set.get(93))).addMembers(member(set.get(94))).addMembers(member(set.get(95))).addMembers(member(set.get(96))).addMembers(member(set.get(97)))
            .addMembers(member(set.get(98))).addMembers(member(set.get(99))).addMembers(member(set.get(100))).
                    addMembers(member(set.get(101))).addMembers(member(set.get(102))).addMembers(member(set.get(103))).addMembers(member(set.get(104))).addMembers(member(set.get(105))).addMembers(member(set.get(106))).addMembers(member(set.get(107)))
            .addMembers(member(set.get(108))).addMembers(member(set.get(109))).addMembers(member(set.get(110))).
                    addMembers(member(set.get(111))).addMembers(member(set.get(112))).addMembers(member(set.get(113))).addMembers(member(set.get(114))).addMembers(member(set.get(115))).addMembers(member(set.get(116))).addMembers(member(set.get(117)))
            .addMembers(member(set.get(118))).addMembers(member(set.get(119))).addMembers(member(set.get(120))).
                    addMembers(member(set.get(121))).addMembers(member(set.get(122))).addMembers(member(set.get(123))).addMembers(member(set.get(124))).addMembers(member(set.get(125))).addMembers(member(set.get(126))).addMembers(member(set.get(127)))
            .addMembers(member(set.get(128))).addMembers(member(set.get(129))).addMembers(member(set.get(130))).
                    addMembers(member(set.get(131))).addMembers(member(set.get(132))).addMembers(member(set.get(133))).addMembers(member(set.get(134))).addMembers(member(set.get(135))).addMembers(member(set.get(136))).addMembers(member(set.get(137)))
            .addMembers(member(set.get(138))).addMembers(member(set.get(139))).addMembers(member(set.get(140))).
                    addMembers(member(set.get(141))).addMembers(member(set.get(142))).addMembers(member(set.get(143))).addMembers(member(set.get(144))).addMembers(member(set.get(145))).addMembers(member(set.get(146))).addMembers(member(set.get(147))).addMembers(member(set.get(148)))
            .addMembers(member(set.get(149))).addMembers(member(set.get(150))).addMembers(member(set.get(151))).addMembers(member(set.get(152))).addMembers(member(set.get(153))).addMembers(member(set.get(154))).addMembers(member(set.get(155))).addMembers(member(set.get(156))).addMembers(member(set.get(157))).addMembers(member(set.get(158))).addMembers(member(set.get(159))).addMembers(member(set.get(160))).addMembers(member(set.get(161))).addMembers(member(set.get(162))).addMembers(member(set.get(163))).addMembers(member(set.get(164))).addMembers(member(set.get(165))).addMembers(member(set.get(166))).addMembers(member(set.get(167))).addMembers(member(set.get(168))).addMembers(member(set.get(169))).addMembers(member(set.get(170))).addMembers(member(set.get(171))).addMembers(member(set.get(172))).addMembers(member(set.get(173))).addMembers(member(set.get(174))).addMembers(member(set.get(175))).addMembers(member(set.get(176))).addMembers(member(set.get(177))).addMembers(member(set.get(178))).addMembers(member(set.get(179))).addMembers(member(set.get(180))).addMembers(member(set.get(181))).addMembers(member(set.get(182))).addMembers(member(set.get(183))).addMembers(member(set.get(184))).addMembers(member(set.get(185))).addMembers(member(set.get(186))).addMembers(member(set.get(187))).addMembers(member(set.get(188))).addMembers(member(set.get(189))).addMembers(member(set.get(190))).addMembers(member(set.get(191))).addMembers(member(set.get(192))).addMembers(member(set.get(193))).addMembers(member(set.get(194))).addMembers(member(set.get(195))).addMembers(member(set.get(196))).addMembers(member(set.get(197))).addMembers(member(set.get(198))).addMembers(member(set.get(199))).addMembers(member(set.get(200))).addMembers(member(set.get(201))).addMembers(member(set.get(202))).addMembers(member(set.get(203))).addMembers(member(set.get(204))).addMembers(member(set.get(205))).addMembers(member(set.get(206))).addMembers(member(set.get(207))).addMembers(member(set.get(208))).addMembers(member(set.get(209))).addMembers(member(set.get(210))).addMembers(member(set.get(211))).addMembers(member(set.get(212))).addMembers(member(set.get(213))).addMembers(member(set.get(214))).addMembers(member(set.get(215))).addMembers(member(set.get(216))).addMembers(member(set.get(217))).addMembers(member(set.get(218))).addMembers(member(set.get(219)))
            .addMembers(member(set.get(220))).addMembers(member(set.get(221))).addMembers(member(set.get(222))).addMembers(member(set.get(223))).addMembers(member(set.get(224))).addMembers(member(set.get(225))).addMembers(member(set.get(226))).addMembers(member(set.get(227))).addMembers(member(set.get(228))).addMembers(member(set.get(229))).addMembers(member(set.get(230))).addMembers(member(set.get(231))).addMembers(member(set.get(232))).addMembers(member(set.get(233))).addMembers(member(set.get(234))).addMembers(member(set.get(235))).addMembers(member(set.get(236))).addMembers(member(set.get(237))).addMembers(member(set.get(238))).addMembers(member(set.get(239))).addMembers(member(set.get(240))).addMembers(member(set.get(241))).addMembers(member(set.get(242))).addMembers(member(set.get(243))).addMembers(member(set.get(244))).addMembers(member(set.get(245))).addMembers(member(set.get(246))).addMembers(member(set.get(247))).addMembers(member(set.get(248))).addMembers(member(set.get(249))).addMembers(member(set.get(250))).addMembers(member(set.get(251))).addMembers(member(set.get(252))).addMembers(member(set.get(253))).addMembers(member(set.get(254))).addMembers(member(set.get(255))).addMembers(member(set.get(256))).addMembers(member(set.get(257))).addMembers(member(set.get(258))).addMembers(member(set.get(259))).addMembers(member(set.get(260))).addMembers(member(set.get(261))).addMembers(member(set.get(262))).addMembers(member(set.get(263))).addMembers(member(set.get(264))).addMembers(member(set.get(265))).addMembers(member(set.get(266))).addMembers(member(set.get(267))).addMembers(member(set.get(268))).addMembers(member(set.get(269))).addMembers(member(set.get(270))).addMembers(member(set.get(271))).addMembers(member(set.get(272))).addMembers(member(set.get(273))).addMembers(member(set.get(274))).addMembers(member(set.get(275))).addMembers(member(set.get(276))).addMembers(member(set.get(277))).addMembers(member(set.get(278))).addMembers(member(set.get(279))).addMembers(member(set.get(280))).addMembers(member(set.get(281))).addMembers(member(set.get(282))).addMembers(member(set.get(283))).addMembers(member(set.get(284))).addMembers(member(set.get(285))).addMembers(member(set.get(286))).addMembers(member(set.get(287))).addMembers(member(set.get(288))).addMembers(member(set.get(289))).addMembers(member(set.get(290))).addMembers(member(set.get(291)))
            .addMembers(member(set.get(292))).addMembers(member(set.get(293))).addMembers(member(set.get(294))).addMembers(member(set.get(295))).addMembers(member(set.get(296))).addMembers(member(set.get(297))).addMembers(member(set.get(298))).addMembers(member(set.get(299))).addMembers(member(set.get(300))).addMembers(member(set.get(301))).addMembers(member(set.get(302))).addMembers(member(set.get(303))).addMembers(member(set.get(304))).addMembers(member(set.get(305))).addMembers(member(set.get(306))).addMembers(member(set.get(307))).addMembers(member(set.get(308))).addMembers(member(set.get(309))).addMembers(member(set.get(310))).addMembers(member(set.get(311))).addMembers(member(set.get(312))).addMembers(member(set.get(313))).addMembers(member(set.get(314))).addMembers(member(set.get(315))).addMembers(member(set.get(316))).addMembers(member(set.get(317))).addMembers(member(set.get(318))).addMembers(member(set.get(319))).addMembers(member(set.get(320))).addMembers(member(set.get(321))).addMembers(member(set.get(322))).addMembers(member(set.get(323))).addMembers(member(set.get(324))).addMembers(member(set.get(325))).addMembers(member(set.get(326))).addMembers(member(set.get(327))).addMembers(member(set.get(328))).addMembers(member(set.get(329))).addMembers(member(set.get(330))).addMembers(member(set.get(331))).addMembers(member(set.get(332))).addMembers(member(set.get(333))).addMembers(member(set.get(334))).addMembers(member(set.get(335))).addMembers(member(set.get(336))).addMembers(member(set.get(337))).addMembers(member(set.get(338))).addMembers(member(set.get(339))).addMembers(member(set.get(340))).addMembers(member(set.get(341))).addMembers(member(set.get(342))).addMembers(member(set.get(343))).addMembers(member(set.get(344))).addMembers(member(set.get(345))).addMembers(member(set.get(346))).addMembers(member(set.get(347))).addMembers(member(set.get(348))).addMembers(member(set.get(349))).addMembers(member(set.get(350))).addMembers(member(set.get(351))).addMembers(member(set.get(352))).addMembers(member(set.get(353))).addMembers(member(set.get(354))).addMembers(member(set.get(355))).addMembers(member(set.get(356))).addMembers(member(set.get(357))).addMembers(member(set.get(358))).addMembers(member(set.get(359))).addMembers(member(set.get(360))).addMembers(member(set.get(361))).addMembers(member(set.get(362))).addMembers(member(set.get(363))).addMembers(member(set.get(364))).addMembers(member(set.get(365))).addMembers(member(set.get(366))).addMembers(member(set.get(367))).addMembers(member(set.get(368))).addMembers(member(set.get(369))).addMembers(member(set.get(370))).addMembers(member(set.get(371))).addMembers(member(set.get(372))).addMembers(member(set.get(373))).addMembers(member(set.get(374))).addMembers(member(set.get(375))).addMembers(member(set.get(376))).addMembers(member(set.get(377))).addMembers(member(set.get(378))).addMembers(member(set.get(379))).addMembers(member(set.get(380))).addMembers(member(set.get(381))).addMembers(member(set.get(382))).addMembers(member(set.get(383))).addMembers(member(set.get(384))).addMembers(member(set.get(385))).addMembers(member(set.get(386))).addMembers(member(set.get(387))).addMembers(member(set.get(388))).addMembers(member(set.get(389))).addMembers(member(set.get(390))).addMembers(member(set.get(391))).addMembers(member(set.get(392))).addMembers(member(set.get(393))).addMembers(member(set.get(394))).addMembers(member(set.get(395))).addMembers(member(set.get(396))).addMembers(member(set.get(397))).addMembers(member(set.get(398))).addMembers(member(set.get(399))).addMembers(member(set.get(400))).addMembers(member(set.get(401))).addMembers(member(set.get(402))).addMembers(member(set.get(403))).addMembers(member(set.get(404))).addMembers(member(set.get(405))).addMembers(member(set.get(406))).addMembers(member(set.get(407))).addMembers(member(set.get(408))).addMembers(member(set.get(409))).addMembers(member(set.get(410))).addMembers(member(set.get(411))).addMembers(member(set.get(412))).addMembers(member(set.get(413))).addMembers(member(set.get(414))).addMembers(member(set.get(415))).addMembers(member(set.get(416))).addMembers(member(set.get(417))).addMembers(member(set.get(418))).addMembers(member(set.get(419))).addMembers(member(set.get(420))).addMembers(member(set.get(421))).addMembers(member(set.get(422))).addMembers(member(set.get(423))).addMembers(member(set.get(424))).addMembers(member(set.get(425))).addMembers(member(set.get(426))).addMembers(member(set.get(427))).addMembers(member(set.get(428))).addMembers(member(set.get(429))).addMembers(member(set.get(430))).addMembers(member(set.get(431))).addMembers(member(set.get(432))).addMembers(member(set.get(433))).addMembers(member(set.get(434))).addMembers(member(set.get(435))).addMembers(member(set.get(436))).addMembers(member(set.get(437))).addMembers(member(set.get(438))).addMembers(member(set.get(439))).addMembers(member(set.get(440))).addMembers(member(set.get(441))).addMembers(member(set.get(442))).addMembers(member(set.get(443))).addMembers(member(set.get(444))).addMembers(member(set.get(445))).addMembers(member(set.get(446))).addMembers(member(set.get(447))).addMembers(member(set.get(448))).addMembers(member(set.get(449))).addMembers(member(set.get(450))).addMembers(member(set.get(451))).addMembers(member(set.get(452))).addMembers(member(set.get(453))).addMembers(member(set.get(454))).addMembers(member(set.get(455))).addMembers(member(set.get(456))).addMembers(member(set.get(457))).addMembers(member(set.get(458))).addMembers(member(set.get(459))).addMembers(member(set.get(460))).addMembers(member(set.get(461))).addMembers(member(set.get(462))).addMembers(member(set.get(463))).addMembers(member(set.get(464))).addMembers(member(set.get(465)))
            .addMembers(member(set.get(466))).addMembers(member(set.get(467))).addMembers(member(set.get(468))).addMembers(member(set.get(469))).addMembers(member(set.get(470))).addMembers(member(set.get(471))).addMembers(member(set.get(472))).addMembers(member(set.get(473))).addMembers(member(set.get(474))).addMembers(member(set.get(475))).addMembers(member(set.get(476))).addMembers(member(set.get(477))).addMembers(member(set.get(478))).addMembers(member(set.get(479))).addMembers(member(set.get(480))).addMembers(member(set.get(481))).addMembers(member(set.get(482))).addMembers(member(set.get(483))).addMembers(member(set.get(484))).addMembers(member(set.get(485))).addMembers(member(set.get(486))).addMembers(member(set.get(487))).addMembers(member(set.get(488))).addMembers(member(set.get(489))).addMembers(member(set.get(490))).addMembers(member(set.get(491))).addMembers(member(set.get(492))).addMembers(member(set.get(493))).addMembers(member(set.get(494))).addMembers(member(set.get(495))).addMembers(member(set.get(496))).addMembers(member(set.get(497))).addMembers(member(set.get(498))).addMembers(member(set.get(499))).addMembers(member(set.get(500))).addMembers(member(set.get(501))).addMembers(member(set.get(502))).addMembers(member(set.get(503))).addMembers(member(set.get(504))).addMembers(member(set.get(505))).addMembers(member(set.get(506))).addMembers(member(set.get(507))).addMembers(member(set.get(508))).addMembers(member(set.get(509))).addMembers(member(set.get(510))).addMembers(member(set.get(511))).addMembers(member(set.get(512))).addMembers(member(set.get(513))).addMembers(member(set.get(514))).addMembers(member(set.get(515))).addMembers(member(set.get(516))).addMembers(member(set.get(517))).addMembers(member(set.get(518))).addMembers(member(set.get(519))).addMembers(member(set.get(520))).addMembers(member(set.get(521))).addMembers(member(set.get(522))).addMembers(member(set.get(523))).addMembers(member(set.get(524))).addMembers(member(set.get(525))).addMembers(member(set.get(526))).addMembers(member(set.get(527))).addMembers(member(set.get(528))).addMembers(member(set.get(529))).addMembers(member(set.get(530))).addMembers(member(set.get(531))).addMembers(member(set.get(532))).addMembers(member(set.get(533))).addMembers(member(set.get(534))).addMembers(member(set.get(535))).addMembers(member(set.get(536))).addMembers(member(set.get(537))).addMembers(member(set.get(538))).addMembers(member(set.get(539))).addMembers(member(set.get(540))).addMembers(member(set.get(541))).addMembers(member(set.get(542))).addMembers(member(set.get(543))).addMembers(member(set.get(544))).addMembers(member(set.get(545))).addMembers(member(set.get(546))).addMembers(member(set.get(547))).addMembers(member(set.get(548))).addMembers(member(set.get(549))).addMembers(member(set.get(550))).addMembers(member(set.get(551))).addMembers(member(set.get(552))).addMembers(member(set.get(553))).addMembers(member(set.get(554))).addMembers(member(set.get(555))).addMembers(member(set.get(556))).addMembers(member(set.get(557))).addMembers(member(set.get(558))).addMembers(member(set.get(559))).addMembers(member(set.get(560))).addMembers(member(set.get(561))).addMembers(member(set.get(562))).addMembers(member(set.get(563))).addMembers(member(set.get(564))).addMembers(member(set.get(565))).addMembers(member(set.get(566))).addMembers(member(set.get(567))).addMembers(member(set.get(568))).addMembers(member(set.get(569))).addMembers(member(set.get(570))).addMembers(member(set.get(571))).addMembers(member(set.get(572))).addMembers(member(set.get(573))).addMembers(member(set.get(574))).addMembers(member(set.get(575))).addMembers(member(set.get(576))).addMembers(member(set.get(577))).addMembers(member(set.get(578))).addMembers(member(set.get(579))).addMembers(member(set.get(580))).addMembers(member(set.get(581))).addMembers(member(set.get(582))).addMembers(member(set.get(583))).addMembers(member(set.get(584))).addMembers(member(set.get(585))).addMembers(member(set.get(586))).addMembers(member(set.get(587))).addMembers(member(set.get(588))).addMembers(member(set.get(589))).addMembers(member(set.get(590))).addMembers(member(set.get(591))).addMembers(member(set.get(592))).addMembers(member(set.get(593))).addMembers(member(set.get(594))).addMembers(member(set.get(595))).addMembers(member(set.get(596))).addMembers(member(set.get(597))).addMembers(member(set.get(598))).addMembers(member(set.get(599))).addMembers(member(set.get(600))).addMembers(member(set.get(601))).addMembers(member(set.get(602))).addMembers(member(set.get(603))).addMembers(member(set.get(604))).addMembers(member(set.get(605))).addMembers(member(set.get(606))).addMembers(member(set.get(607))).addMembers(member(set.get(608))).addMembers(member(set.get(609))).addMembers(member(set.get(610))).addMembers(member(set.get(611))).addMembers(member(set.get(612))).addMembers(member(set.get(613))).addMembers(member(set.get(614))).addMembers(member(set.get(615))).addMembers(member(set.get(616))).addMembers(member(set.get(617))).addMembers(member(set.get(618))).addMembers(member(set.get(619))).addMembers(member(set.get(620))).addMembers(member(set.get(621))).addMembers(member(set.get(622))).addMembers(member(set.get(623))).addMembers(member(set.get(624))).addMembers(member(set.get(625))).addMembers(member(set.get(626))).addMembers(member(set.get(627))).addMembers(member(set.get(628))).addMembers(member(set.get(629))).addMembers(member(set.get(630))).addMembers(member(set.get(631))).addMembers(member(set.get(632))).addMembers(member(set.get(633))).addMembers(member(set.get(634))).addMembers(member(set.get(635))).addMembers(member(set.get(636))).addMembers(member(set.get(637))).addMembers(member(set.get(638))).addMembers(member(set.get(639)))
            .addMembers(member(set.get(640))).addMembers(member(set.get(641))).addMembers(member(set.get(642))).addMembers(member(set.get(643))).addMembers(member(set.get(644))).addMembers(member(set.get(645))).addMembers(member(set.get(646))).addMembers(member(set.get(647))).addMembers(member(set.get(648))).addMembers(member(set.get(649))).addMembers(member(set.get(650))).addMembers(member(set.get(651))).addMembers(member(set.get(652))).addMembers(member(set.get(653))).addMembers(member(set.get(654))).addMembers(member(set.get(655))).addMembers(member(set.get(656))).addMembers(member(set.get(657))).addMembers(member(set.get(658))).addMembers(member(set.get(659))).addMembers(member(set.get(660))).addMembers(member(set.get(661))).addMembers(member(set.get(662))).addMembers(member(set.get(663))).addMembers(member(set.get(664))).addMembers(member(set.get(665))).addMembers(member(set.get(666))).addMembers(member(set.get(667))).addMembers(member(set.get(668))).addMembers(member(set.get(669))).addMembers(member(set.get(670))).addMembers(member(set.get(671))).addMembers(member(set.get(672))).addMembers(member(set.get(673))).addMembers(member(set.get(674))).addMembers(member(set.get(675))).addMembers(member(set.get(676))).addMembers(member(set.get(677))).addMembers(member(set.get(678))).addMembers(member(set.get(679))).addMembers(member(set.get(680))).addMembers(member(set.get(681))).addMembers(member(set.get(682))).addMembers(member(set.get(683))).addMembers(member(set.get(684))).addMembers(member(set.get(685))).addMembers(member(set.get(686))).addMembers(member(set.get(687))).addMembers(member(set.get(688))).addMembers(member(set.get(689))).addMembers(member(set.get(690))).addMembers(member(set.get(691))).addMembers(member(set.get(692))).addMembers(member(set.get(693))).addMembers(member(set.get(694))).addMembers(member(set.get(695))).addMembers(member(set.get(696))).addMembers(member(set.get(697))).addMembers(member(set.get(698))).addMembers(member(set.get(699))).addMembers(member(set.get(700))).addMembers(member(set.get(701))).addMembers(member(set.get(702))).addMembers(member(set.get(703))).addMembers(member(set.get(704))).addMembers(member(set.get(705))).addMembers(member(set.get(706))).addMembers(member(set.get(707))).addMembers(member(set.get(708))).addMembers(member(set.get(709))).addMembers(member(set.get(710))).addMembers(member(set.get(711))).addMembers(member(set.get(712))).addMembers(member(set.get(713))).addMembers(member(set.get(714))).addMembers(member(set.get(715))).addMembers(member(set.get(716))).addMembers(member(set.get(717))).addMembers(member(set.get(718))).addMembers(member(set.get(719))).addMembers(member(set.get(720))).addMembers(member(set.get(721))).addMembers(member(set.get(722))).addMembers(member(set.get(723))).addMembers(member(set.get(724))).addMembers(member(set.get(725))).addMembers(member(set.get(726))).addMembers(member(set.get(727))).addMembers(member(set.get(728))).addMembers(member(set.get(729))).addMembers(member(set.get(730))).addMembers(member(set.get(731))).addMembers(member(set.get(732))).addMembers(member(set.get(733))).addMembers(member(set.get(734))).addMembers(member(set.get(735))).addMembers(member(set.get(736))).addMembers(member(set.get(737))).addMembers(member(set.get(738))).addMembers(member(set.get(739))).addMembers(member(set.get(740))).addMembers(member(set.get(741))).addMembers(member(set.get(742))).addMembers(member(set.get(743))).addMembers(member(set.get(744))).addMembers(member(set.get(745))).addMembers(member(set.get(746))).addMembers(member(set.get(747))).addMembers(member(set.get(748))).addMembers(member(set.get(749))).addMembers(member(set.get(750))).addMembers(member(set.get(751))).addMembers(member(set.get(752))).addMembers(member(set.get(753))).addMembers(member(set.get(754))).addMembers(member(set.get(755))).addMembers(member(set.get(756))).addMembers(member(set.get(757))).addMembers(member(set.get(758))).addMembers(member(set.get(759))).addMembers(member(set.get(760))).addMembers(member(set.get(761))).addMembers(member(set.get(762))).addMembers(member(set.get(763))).addMembers(member(set.get(764))).addMembers(member(set.get(765))).addMembers(member(set.get(766))).addMembers(member(set.get(767))).addMembers(member(set.get(768))).addMembers(member(set.get(769))).addMembers(member(set.get(770))).addMembers(member(set.get(771))).addMembers(member(set.get(772))).addMembers(member(set.get(773))).addMembers(member(set.get(774))).addMembers(member(set.get(775))).addMembers(member(set.get(776))).addMembers(member(set.get(777))).addMembers(member(set.get(778))).addMembers(member(set.get(779))).addMembers(member(set.get(780))).addMembers(member(set.get(781))).addMembers(member(set.get(782))).addMembers(member(set.get(783))).addMembers(member(set.get(784))).addMembers(member(set.get(785))).addMembers(member(set.get(786))).addMembers(member(set.get(787))).addMembers(member(set.get(788))).addMembers(member(set.get(789))).addMembers(member(set.get(790))).addMembers(member(set.get(791))).addMembers(member(set.get(792))).addMembers(member(set.get(793))).addMembers(member(set.get(794))).addMembers(member(set.get(795))).addMembers(member(set.get(796))).addMembers(member(set.get(797))).addMembers(member(set.get(798))).addMembers(member(set.get(799))).addMembers(member(set.get(800))).addMembers(member(set.get(801))).addMembers(member(set.get(802))).addMembers(member(set.get(803))).addMembers(member(set.get(804))).addMembers(member(set.get(805))).addMembers(member(set.get(806))).addMembers(member(set.get(807))).addMembers(member(set.get(808))).addMembers(member(set.get(809))).addMembers(member(set.get(810))).addMembers(member(set.get(811))).addMembers(member(set.get(812))).addMembers(member(set.get(813)))
            .addMembers(member(set.get(814))).addMembers(member(set.get(815))).addMembers(member(set.get(816))).addMembers(member(set.get(817))).addMembers(member(set.get(818))).addMembers(member(set.get(819))).addMembers(member(set.get(820))).addMembers(member(set.get(821))).addMembers(member(set.get(822))).addMembers(member(set.get(823))).addMembers(member(set.get(824))).addMembers(member(set.get(825))).addMembers(member(set.get(826))).addMembers(member(set.get(827))).addMembers(member(set.get(828))).addMembers(member(set.get(829))).addMembers(member(set.get(830))).addMembers(member(set.get(831))).addMembers(member(set.get(832))).addMembers(member(set.get(833))).addMembers(member(set.get(834))).addMembers(member(set.get(835))).addMembers(member(set.get(836))).addMembers(member(set.get(837))).addMembers(member(set.get(838))).addMembers(member(set.get(839))).addMembers(member(set.get(840))).addMembers(member(set.get(841))).addMembers(member(set.get(842))).addMembers(member(set.get(843))).addMembers(member(set.get(844))).addMembers(member(set.get(845))).addMembers(member(set.get(846))).addMembers(member(set.get(847))).addMembers(member(set.get(848))).addMembers(member(set.get(849))).addMembers(member(set.get(850))).addMembers(member(set.get(851))).addMembers(member(set.get(852))).addMembers(member(set.get(853))).addMembers(member(set.get(854))).addMembers(member(set.get(855))).addMembers(member(set.get(856))).addMembers(member(set.get(857))).addMembers(member(set.get(858))).addMembers(member(set.get(859))).addMembers(member(set.get(860))).addMembers(member(set.get(861))).addMembers(member(set.get(862))).addMembers(member(set.get(863))).addMembers(member(set.get(864))).addMembers(member(set.get(865))).addMembers(member(set.get(866))).addMembers(member(set.get(867))).addMembers(member(set.get(868))).addMembers(member(set.get(869))).addMembers(member(set.get(870))).addMembers(member(set.get(871))).addMembers(member(set.get(872))).addMembers(member(set.get(873))).addMembers(member(set.get(874))).addMembers(member(set.get(875))).addMembers(member(set.get(876))).addMembers(member(set.get(877))).addMembers(member(set.get(878))).addMembers(member(set.get(879))).addMembers(member(set.get(880))).addMembers(member(set.get(881))).addMembers(member(set.get(882))).addMembers(member(set.get(883))).addMembers(member(set.get(884))).addMembers(member(set.get(885))).addMembers(member(set.get(886))).addMembers(member(set.get(887))).addMembers(member(set.get(888))).addMembers(member(set.get(889))).addMembers(member(set.get(890))).addMembers(member(set.get(891))).addMembers(member(set.get(892))).addMembers(member(set.get(893))).addMembers(member(set.get(894))).addMembers(member(set.get(895))).addMembers(member(set.get(896))).addMembers(member(set.get(897))).addMembers(member(set.get(898))).addMembers(member(set.get(899))).addMembers(member(set.get(900))).addMembers(member(set.get(901))).addMembers(member(set.get(902))).addMembers(member(set.get(903))).addMembers(member(set.get(904))).addMembers(member(set.get(905))).addMembers(member(set.get(906))).addMembers(member(set.get(907))).addMembers(member(set.get(908))).addMembers(member(set.get(909))).addMembers(member(set.get(910))).addMembers(member(set.get(911))).addMembers(member(set.get(912))).addMembers(member(set.get(913))).addMembers(member(set.get(914))).addMembers(member(set.get(915))).addMembers(member(set.get(916))).addMembers(member(set.get(917))).addMembers(member(set.get(918))).addMembers(member(set.get(919))).addMembers(member(set.get(920))).addMembers(member(set.get(921))).addMembers(member(set.get(922))).addMembers(member(set.get(923))).addMembers(member(set.get(924))).addMembers(member(set.get(925))).addMembers(member(set.get(926))).addMembers(member(set.get(927))).addMembers(member(set.get(928))).addMembers(member(set.get(929))).addMembers(member(set.get(930))).addMembers(member(set.get(931))).addMembers(member(set.get(932))).addMembers(member(set.get(933))).addMembers(member(set.get(934))).addMembers(member(set.get(935))).addMembers(member(set.get(936))).addMembers(member(set.get(937))).addMembers(member(set.get(938))).addMembers(member(set.get(939))).addMembers(member(set.get(940))).addMembers(member(set.get(941))).addMembers(member(set.get(942))).addMembers(member(set.get(943))).addMembers(member(set.get(944))).addMembers(member(set.get(945))).addMembers(member(set.get(946))).addMembers(member(set.get(947))).addMembers(member(set.get(948))).addMembers(member(set.get(949))).addMembers(member(set.get(950))).addMembers(member(set.get(951))).addMembers(member(set.get(952))).addMembers(member(set.get(953))).addMembers(member(set.get(954))).addMembers(member(set.get(955))).addMembers(member(set.get(956))).addMembers(member(set.get(957))).addMembers(member(set.get(958))).addMembers(member(set.get(959))).addMembers(member(set.get(960))).addMembers(member(set.get(961))).addMembers(member(set.get(962))).addMembers(member(set.get(963))).addMembers(member(set.get(964))).addMembers(member(set.get(965))).addMembers(member(set.get(966))).addMembers(member(set.get(967))).addMembers(member(set.get(968))).addMembers(member(set.get(969))).addMembers(member(set.get(970))).addMembers(member(set.get(971))).addMembers(member(set.get(972))).addMembers(member(set.get(973))).addMembers(member(set.get(974))).addMembers(member(set.get(975))).addMembers(member(set.get(976))).addMembers(member(set.get(977))).addMembers(member(set.get(978))).addMembers(member(set.get(979))).addMembers(member(set.get(980))).addMembers(member(set.get(981))).addMembers(member(set.get(982))).addMembers(member(set.get(983))).addMembers(member(set.get(984))).addMembers(member(set.get(985))).addMembers(member(set.get(986))).addMembers(member(set.get(987)))
            .addMembers(member(set.get(988))).addMembers(member(set.get(989))).addMembers(member(set.get(990))).addMembers(member(set.get(991))).addMembers(member(set.get(992))).addMembers(member(set.get(993))).addMembers(member(set.get(994))).addMembers(member(set.get(995))).addMembers(member(set.get(996))).addMembers(member(set.get(997))).addMembers(member(set.get(998))).addMembers(member(set.get(999))).addMembers(member(set.get(1000)))
            .build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().addNewMembers(member(set.get(1000))).build(), decryptedGroupChange);
  }
  
  @Test
  public void new_invite() {
    UUID           uuidNew = UUID.randomUUID();
    DecryptedGroup from    = DecryptedGroup.newBuilder().build();
    DecryptedGroup to      = DecryptedGroup.newBuilder().addPendingMembers(pendingMember(uuidNew)).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().addNewPendingMembers(pendingMember(uuidNew)).build(), decryptedGroupChange);
  }

  @Test
  public void to_admin() {
    UUID           uuid       = UUID.randomUUID();
    ProfileKey     profileKey = randomProfileKey();
    DecryptedGroup from       = DecryptedGroup.newBuilder().addMembers(withProfileKey(member(uuid), profileKey)).build();
    DecryptedGroup to         = DecryptedGroup.newBuilder().addMembers(withProfileKey(admin(uuid), profileKey)).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().addModifyMemberRoles(promoteAdmin(uuid)).build(), decryptedGroupChange);
  }

  @Test
  public void to_member() {
    UUID           uuid       = UUID.randomUUID();
    ProfileKey     profileKey = randomProfileKey();
    DecryptedGroup from       = DecryptedGroup.newBuilder().addMembers(withProfileKey(admin(uuid), profileKey)).build();
    DecryptedGroup to         = DecryptedGroup.newBuilder().addMembers(withProfileKey(member(uuid), profileKey)).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().addModifyMemberRoles(demoteAdmin(uuid)).build(), decryptedGroupChange);
  }

  @Test
  public void profile_key_change_member() {
    UUID           uuid        = UUID.randomUUID();
    ProfileKey     profileKey1 = randomProfileKey();
    ProfileKey     profileKey2 = randomProfileKey();
    DecryptedGroup from        = DecryptedGroup.newBuilder().addMembers(withProfileKey(admin(uuid),profileKey1)).build();
    DecryptedGroup to          = DecryptedGroup.newBuilder().addMembers(withProfileKey(admin(uuid),profileKey2)).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().addModifiedProfileKeys(withProfileKey(admin(uuid),profileKey2)).build(), decryptedGroupChange);
  }

  @Test
  public void new_invite_access() {
    DecryptedGroup from = DecryptedGroup.newBuilder()
                                        .setAccessControl(AccessControl.newBuilder()
                                                                       .setAddFromInviteLink(AccessControl.AccessRequired.ADMINISTRATOR))
                                        .build();
    DecryptedGroup to   = DecryptedGroup.newBuilder()
                                        .setAccessControl(AccessControl.newBuilder()
                                                                       .setAddFromInviteLink(AccessControl.AccessRequired.UNSATISFIABLE))
                                        .build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder()
                                     .setNewInviteLinkAccess(AccessControl.AccessRequired.UNSATISFIABLE)
                                     .build(),
                 decryptedGroupChange);
  }

  @Test
  public void new_requesting_members() {
    UUID           member1     = UUID.randomUUID();
    ProfileKey     profileKey1 = newProfileKey();
    DecryptedGroup from        = DecryptedGroup.newBuilder()
                                               .build();
    DecryptedGroup to          = DecryptedGroup.newBuilder()
                                               .addRequestingMembers(requestingMember(member1, profileKey1))
                                               .build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder()
                                     .addNewRequestingMembers(requestingMember(member1, profileKey1))
                                     .build(),
                 decryptedGroupChange);
  }

  @Test
  public void new_requesting_members_ignores_existing_by_uuid() {
    UUID           member1     = UUID.randomUUID();
    UUID           member2     = UUID.randomUUID();
    ProfileKey     profileKey2 = newProfileKey();
    DecryptedGroup from        = DecryptedGroup.newBuilder()
                                               .addRequestingMembers(requestingMember(member1, newProfileKey()))
                                               .build();
    DecryptedGroup to          = DecryptedGroup.newBuilder()
                                               .addRequestingMembers(requestingMember(member1, newProfileKey()))
                                               .addRequestingMembers(requestingMember(member2, profileKey2))
                                               .build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder()
                                     .addNewRequestingMembers(requestingMember(member2, profileKey2))
                                     .build(),
                 decryptedGroupChange);
  }

  @Test
  public void removed_requesting_members() {
    UUID           member1 = UUID.randomUUID();
    DecryptedGroup from    = DecryptedGroup.newBuilder()
                                           .addRequestingMembers(requestingMember(member1, newProfileKey()))
                                           .build();
    DecryptedGroup to      = DecryptedGroup.newBuilder()
                                           .build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder()
                                     .addDeleteRequestingMembers(UuidUtil.toByteString(member1))
                                     .build(),
                 decryptedGroupChange);
  }

  @Test
  public void promote_requesting_members() {
    UUID           member1     = UUID.randomUUID();
    ProfileKey     profileKey1 = newProfileKey();
    UUID           member2     = UUID.randomUUID();
    ProfileKey     profileKey2 = newProfileKey();
    DecryptedGroup from        = DecryptedGroup.newBuilder()
                                               .addRequestingMembers(requestingMember(member1, profileKey1))
                                               .addRequestingMembers(requestingMember(member2, profileKey2))
                                               .build();
    DecryptedGroup to          = DecryptedGroup.newBuilder()
                                               .addMembers(member(member1, profileKey1))
                                               .addMembers(admin(member2, profileKey2))
                                               .build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder()
                                     .addPromoteRequestingMembers(approveMember(member1))
                                     .addPromoteRequestingMembers(approveAdmin(member2))
                                     .build(),
                 decryptedGroupChange);
  }

  @Test
  public void new_invite_link_password() {
    ByteString     password1 = ByteString.copyFrom(Util.getSecretBytes(16));
    ByteString     password2 = ByteString.copyFrom(Util.getSecretBytes(16));
    DecryptedGroup from      = DecryptedGroup.newBuilder()
                                             .setInviteLinkPassword(password1)
                                             .build();
    DecryptedGroup to        = DecryptedGroup.newBuilder()
                                             .setInviteLinkPassword(password2)
                                             .build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder()
                                     .setNewInviteLinkPassword(password2)
                                     .build(),
                 decryptedGroupChange);
  }
}
