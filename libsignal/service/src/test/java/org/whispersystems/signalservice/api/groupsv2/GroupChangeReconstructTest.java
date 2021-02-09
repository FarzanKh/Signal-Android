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
