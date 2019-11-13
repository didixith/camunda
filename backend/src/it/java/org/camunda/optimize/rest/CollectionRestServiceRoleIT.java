/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.apache.http.HttpStatus;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.SimpleCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_EMAIL_DOMAIN;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FIRSTNAME;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_LASTNAME;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class CollectionRestServiceRoleIT extends AbstractIT {

  private static final String USER_KERMIT = "kermit";
  private static final String TEST_GROUP = "testGroup";
  private static final String TEST_GROUP_B = "anotherTestGroup";
  private static final String USER_MISS_PIGGY = "MissPiggy";

  @Test
  public void partialCollectionUpdateDoesNotAffectRoles() {
    //given
    final String collectionId = createNewCollection();
    final SimpleCollectionDefinitionDto expectedCollection = getCollection(collectionId);

    // when
    final PartialCollectionDefinitionDto collectionRenameDto = new PartialCollectionDefinitionDto("Test");
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdatePartialCollectionRequest(collectionId, collectionRenameDto)
      .execute();

    // then
    assertThat(response.getStatus(), is(204));
    final SimpleCollectionDefinitionDto collection = getCollection(collectionId);
    assertThat(collection.getData().getRoles(), is(expectedCollection.getData().getRoles()));
  }

  @Test
  public void getRoles() {
    //given
    final String collectionId = createNewCollection();
    final SimpleCollectionDefinitionDto expectedCollection = getCollection(collectionId);

    // when
    List<CollectionRoleDto> roles = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetRolesToCollectionRequest(collectionId)
      .executeAndReturnList(CollectionRoleDto.class, 200);

    // then
    assertThat(roles.size(), is(1));
    assertThat(roles, is(expectedCollection.getData().getRoles()));
  }

  @Test
  public void getRolesSortedCorrectly() {
    // given
    final String collectionId = createNewCollection();
    engineIntegrationExtension.createGroup(TEST_GROUP, TEST_GROUP);
    engineIntegrationExtension.createGroup(TEST_GROUP_B, TEST_GROUP_B);
    engineIntegrationExtension.addUser(USER_KERMIT, USER_KERMIT);
    engineIntegrationExtension.grantUserOptimizeAccess(USER_KERMIT);
    engineIntegrationExtension.addUser(USER_MISS_PIGGY, USER_MISS_PIGGY);
    engineIntegrationExtension.grantUserOptimizeAccess(USER_MISS_PIGGY);

    GroupDto testGroupDto = new GroupDto(TEST_GROUP, TEST_GROUP);
    GroupDto anotherTestGroupDto = new GroupDto(TEST_GROUP_B, TEST_GROUP_B);
    UserDto kermitUserDto = new UserDto(USER_KERMIT, USER_KERMIT);
    UserDto missPiggyUserDto = new UserDto(USER_MISS_PIGGY, USER_MISS_PIGGY);
    UserDto demoUserDto = new UserDto(DEFAULT_USERNAME, DEFAULT_USERNAME);

    List<IdentityDto> identities = new ArrayList<>();
    identities.add(testGroupDto);
    identities.add(anotherTestGroupDto);
    identities.add(kermitUserDto);
    identities.add(missPiggyUserDto);

    identities.forEach(i -> embeddedOptimizeExtension.getIdentityService().addIdentity(i));

    // TODO after OPT-2891 is fixed, addRoleToCollection can be called with CollectionRoleDtos whose identity name is
    //  non null --> move addRoleToCollection calls to forEach loop above once OPT-2891 is done
    addRoleToCollection(collectionId, new CollectionRoleDto(new GroupDto(TEST_GROUP), RoleType.EDITOR));
    addRoleToCollection(collectionId, new CollectionRoleDto(new GroupDto(TEST_GROUP_B), RoleType.EDITOR));
    addRoleToCollection(collectionId, new CollectionRoleDto(new UserDto(USER_KERMIT), RoleType.EDITOR));
    addRoleToCollection(collectionId, new CollectionRoleDto(new UserDto(USER_MISS_PIGGY), RoleType.EDITOR));

    // when
    List<CollectionRoleDto> roles = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetRolesToCollectionRequest(collectionId)
      .executeAndReturnList(CollectionRoleDto.class, 200);

    // then
    // expected oder(groups first, user second, then by name ascending):
    // anotherTestGroupRole, testGroupRole, demoManagerRole, kermitRole, missPiggyRole
    assertThat(roles.size(), is(identities.size() + 1)); // +1 for demo manager role
    assertThat(roles.get(0).getIdentity(), is(anotherTestGroupDto));
    assertThat(roles.get(1).getIdentity(), is(testGroupDto));
    assertThat(roles.get(2).getIdentity(), is(demoUserDto));
    assertThat(roles.get(3).getIdentity(), is(kermitUserDto));
    assertThat(roles.get(4).getIdentity(), is(missPiggyUserDto));
  }

  @Test
  public void getRolesContainsUserMetadata_retrieveFromCache() {
    //given
    final String collectionId = createNewCollection();

    UserDto expectedUserDtoWithData =
      new UserDto(DEFAULT_USERNAME, DEFAULT_FIRSTNAME, DEFAULT_LASTNAME, "me@camunda.com");

    embeddedOptimizeExtension.getIdentityService().addIdentity(expectedUserDtoWithData);

    // when
    List<CollectionRoleDto> roles = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetRolesToCollectionRequest(collectionId)
      .executeAndReturnList(CollectionRoleDto.class, 200);

    // then
    assertThat(roles.size(), is(1));
    final IdentityDto identityDto = roles.get(0).getIdentity();
    assertThat(identityDto, is(instanceOf(UserDto.class)));
    final UserDto userDto = (UserDto) identityDto;
    assertThat(userDto.getFirstName(), is(expectedUserDtoWithData.getFirstName()));
    assertThat(userDto.getLastName(), is(expectedUserDtoWithData.getLastName()));
    assertThat(
      userDto.getName(),
      is(expectedUserDtoWithData.getFirstName() + " " + expectedUserDtoWithData.getLastName())
    );
    assertThat(userDto.getEmail(), is(expectedUserDtoWithData.getEmail()));
  }

  @Test
  public void getRolesContainsUserMetadata_fetchIfNotInCache() {
    //given
    final String collectionId = createNewCollection();

    // when
    List<CollectionRoleDto> roles = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetRolesToCollectionRequest(collectionId)
      .executeAndReturnList(CollectionRoleDto.class, 200);

    // then
    assertThat(roles.size(), is(1));
    final IdentityDto identityDto = roles.get(0).getIdentity();
    assertThat(identityDto, is(instanceOf(UserDto.class)));
    final UserDto userDto = (UserDto) identityDto;
    assertThat(userDto.getId(), is(DEFAULT_USERNAME));
    assertThat(userDto.getFirstName(), is(DEFAULT_FIRSTNAME));
    assertThat(userDto.getLastName(), is(DEFAULT_LASTNAME));
    assertThat(
      userDto.getName(),
      is(DEFAULT_FIRSTNAME + " " + DEFAULT_LASTNAME)
    );
    assertThat(userDto.getEmail(), endsWith(DEFAULT_EMAIL_DOMAIN));
  }

  @Test
  public void getRolesContainsGroupMetadata() {
    //given
    final String collectionId = createNewCollection();
    engineIntegrationExtension.createGroup(TEST_GROUP, TEST_GROUP);
    engineIntegrationExtension.addUser(USER_KERMIT, USER_KERMIT);
    engineIntegrationExtension.addUserToGroup(USER_KERMIT, TEST_GROUP);
    engineIntegrationExtension.addUser(USER_MISS_PIGGY, USER_MISS_PIGGY);
    engineIntegrationExtension.addUserToGroup(USER_MISS_PIGGY, TEST_GROUP);

    final CollectionRoleDto roleDto = new CollectionRoleDto(new GroupDto(TEST_GROUP), RoleType.EDITOR);
    addRoleToCollection(collectionId, roleDto);

    // when
    List<CollectionRoleDto> roles = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetRolesToCollectionRequest(collectionId)
      .executeAndReturnList(CollectionRoleDto.class, 200);

    // then
    final List<IdentityDto> groupIdentities = roles.stream()
      .map(CollectionRoleDto::getIdentity)
      .filter(identityDto -> identityDto instanceof GroupDto)
      .collect(Collectors.toList());
    assertThat(groupIdentities.size(), is(1));

    final GroupDto expectedGroupDto = new GroupDto(TEST_GROUP, TEST_GROUP, 2L);
    final GroupDto actualGroupDto = (GroupDto) groupIdentities.get(0);
    assertThat(actualGroupDto, is(expectedGroupDto));
  }

  @Test
  public void getRolesNoGroupMetadataAvailable() {
    //given
    final String collectionId = createNewCollection();
    engineIntegrationExtension.createGroup(TEST_GROUP, null);

    final CollectionRoleDto roleDto = new CollectionRoleDto(new GroupDto(TEST_GROUP), RoleType.EDITOR);
    addRoleToCollection(collectionId, roleDto);

    // when
    List<CollectionRoleDto> roles = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetRolesToCollectionRequest(collectionId)
      .executeAndReturnList(CollectionRoleDto.class, 200);

    // then
    final List<IdentityDto> groupIdentities = roles.stream()
      .map(CollectionRoleDto::getIdentity)
      .filter(identityDto -> identityDto instanceof GroupDto)
      .collect(Collectors.toList());
    assertThat(groupIdentities.size(), is(1));

    final GroupDto groupDto = (GroupDto) groupIdentities.get(0);
    assertThat(groupDto.getName(), is(nullValue()));
  }

  @Test
  public void addUserRole() {
    // given
    final String collectionId = createNewCollection();
    engineIntegrationExtension.addUser(USER_KERMIT, USER_KERMIT);
    engineIntegrationExtension.grantUserOptimizeAccess(USER_KERMIT);

    // when
    final CollectionRoleDto roleDto = new CollectionRoleDto(new UserDto(USER_KERMIT), RoleType.EDITOR);
    IdDto idDto = addRoleToCollection(collectionId, roleDto);

    // then
    assertThat(idDto.getId(), is(roleDto.getId()));
    final SimpleCollectionDefinitionDto collection = getCollection(collectionId);
    assertThat(collection.getData().getRoles(), hasItem(roleDto));
  }

  @Test
  public void addMultipleUserRoles() {
    // given
    final String collectionId = createNewCollection();
    engineIntegrationExtension.addUser(USER_KERMIT, USER_KERMIT);
    engineIntegrationExtension.grantUserOptimizeAccess(USER_KERMIT);
    engineIntegrationExtension.addUser(USER_MISS_PIGGY, USER_MISS_PIGGY);
    engineIntegrationExtension.grantUserOptimizeAccess(USER_MISS_PIGGY);

    // when
    final CollectionRoleDto kermitRoleDto = new CollectionRoleDto(new UserDto(USER_KERMIT), RoleType.EDITOR);
    IdDto kermitRoleIdDto = addRoleToCollection(collectionId, kermitRoleDto);

    final CollectionRoleDto missPiggyRoleDto = new CollectionRoleDto(new UserDto(USER_MISS_PIGGY), RoleType.VIEWER);
    IdDto missPiggyIdDto = addRoleToCollection(collectionId, missPiggyRoleDto);

    // then
    assertThat(kermitRoleIdDto.getId(), is(kermitRoleDto.getId()));
    assertThat(missPiggyIdDto.getId(), is(missPiggyRoleDto.getId()));

    final SimpleCollectionDefinitionDto collection = getCollection(collectionId);
    assertThat(collection.getData().getRoles(), hasItems(kermitRoleDto, missPiggyRoleDto));
  }

  @Test
  public void addUserRoleFailsForUnknownUsers() {
    // given
    final String collectionId = createNewCollection();

    // when
    final CollectionRoleDto roleDto = new CollectionRoleDto(new UserDto(USER_KERMIT), RoleType.EDITOR);
    final Response addRoleResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildAddRoleToCollectionRequest(collectionId, roleDto)
      .execute();

    // then
    assertThat(addRoleResponse.getStatus(), is(400));
  }

  @Test
  public void addUserRoleFailsNotExistingUser() {
    // given
    final String collectionId = createNewCollection();

    // when
    final CollectionRoleDto roleDto = new CollectionRoleDto(new UserDto(USER_KERMIT), RoleType.EDITOR);
    final Response addRoleResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildAddRoleToCollectionRequest(collectionId, roleDto)
      .execute();

    // then
    assertThat(addRoleResponse.getStatus(), is(400));
  }

  @Test
  public void addGroupRole() {
    // given
    final String collectionId = createNewCollection();
    engineIntegrationExtension.createGroup(TEST_GROUP, TEST_GROUP);

    // when
    final CollectionRoleDto roleDto = new CollectionRoleDto(new GroupDto(TEST_GROUP), RoleType.EDITOR);
    IdDto idDto = addRoleToCollection(collectionId, roleDto);

    // then
    assertThat(idDto.getId(), is(roleDto.getId()));
    final SimpleCollectionDefinitionDto collection = getCollection(collectionId);
    assertThat(collection.getData().getRoles(), hasItem(roleDto));
  }

  @Test
  public void addGroupRoleFailsNotExistingGroup() {
    // given
    final String collectionId = createNewCollection();

    // when
    final CollectionRoleDto roleDto = new CollectionRoleDto(new UserDto(TEST_GROUP), RoleType.EDITOR);
    final Response addRoleResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildAddRoleToCollectionRequest(collectionId, roleDto)
      .execute();

    // then
    assertThat(addRoleResponse.getStatus(), is(400));
  }

  @Test
  public void duplicateIdentityRoleIsRejected() {
    // given
    final String collectionId = createNewCollection();
    final SimpleCollectionDefinitionDto expectedCollection = getCollection(collectionId);

    // when
    final CollectionRoleDto roleDto = new CollectionRoleDto(
      // there is already an entry for the default user who created the collection
      new UserDto(DEFAULT_USERNAME),
      RoleType.EDITOR
    );
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildAddRoleToCollectionRequest(collectionId, roleDto)
      .execute();

    // then
    assertThat(response.getStatus(), is(HttpStatus.SC_CONFLICT));
    final ConflictResponseDto conflictResponseDto = response.readEntity(ConflictResponseDto.class);
    assertThat(conflictResponseDto.getErrorMessage(), is(notNullValue()));

    assertThat(getCollection(collectionId), is(expectedCollection));
  }

  @Test
  public void roleCanGetUpdated() {
    // given
    final String collectionId = createNewCollection();
    engineIntegrationExtension.addUser(USER_KERMIT, USER_KERMIT);
    engineIntegrationExtension.grantUserOptimizeAccess(USER_KERMIT);

    // when
    final IdentityDto identityDto = new UserDto(USER_KERMIT);
    final CollectionRoleDto roleDto = new CollectionRoleDto(identityDto, RoleType.EDITOR);
    addRoleToCollection(collectionId, roleDto);

    final CollectionRoleUpdateDto updatedRoleDto = new CollectionRoleUpdateDto(RoleType.VIEWER);
    updateRoleOnCollection(collectionId, roleDto.getId(), updatedRoleDto);

    // then
    final SimpleCollectionDefinitionDto collection = getCollection(collectionId);
    assertThat(collection.getData().getRoles().size(), is(2));
    assertThat(collection.getData().getRoles(), hasItem(new CollectionRoleDto(identityDto, RoleType.VIEWER)));
  }

  @Test
  public void updatingLastManagerFails() {
    // given
    final String collectionId = createNewCollection();
    final SimpleCollectionDefinitionDto expectedCollection = getCollection(collectionId);
    final CollectionRoleDto roleEntryDto = expectedCollection.getData().getRoles().get(0);

    // when
    final CollectionRoleUpdateDto updatedRoleDto = new CollectionRoleUpdateDto(RoleType.EDITOR);
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateRoleToCollectionRequest(collectionId, roleEntryDto.getId(), updatedRoleDto)
      .execute();

    // then
    assertThat(response.getStatus(), is(HttpStatus.SC_CONFLICT));
    final ConflictResponseDto conflictResponseDto = response.readEntity(ConflictResponseDto.class);
    assertThat(conflictResponseDto.getErrorMessage(), is(notNullValue()));

    assertThat(getCollection(collectionId), is(expectedCollection));
  }

  @Test
  public void updatingNonPresentRoleFails() {
    // given
    final String collectionId = createNewCollection();
    final SimpleCollectionDefinitionDto expectedCollection = getCollection(collectionId);
    final String notExistingRoleEntryId = "USER:abc";

    // when
    final CollectionRoleUpdateDto updatedRoleDto = new CollectionRoleUpdateDto(RoleType.EDITOR);
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateRoleToCollectionRequest(collectionId, notExistingRoleEntryId, updatedRoleDto)
      .execute();

    // then
    assertThat(response.getStatus(), is(HttpStatus.SC_NOT_FOUND));

    assertThat(getCollection(collectionId), is(expectedCollection));
  }

  @Test
  public void roleCanGetDeleted() {
    // given
    final String collectionId = createNewCollection();
    engineIntegrationExtension.addUser(USER_KERMIT, USER_KERMIT);
    engineIntegrationExtension.grantUserOptimizeAccess(USER_KERMIT);

    // when
    final IdentityDto identityDto = new UserDto(USER_KERMIT);
    final CollectionRoleDto roleDto = new CollectionRoleDto(identityDto, RoleType.EDITOR);
    addRoleToCollection(collectionId, roleDto);
    deleteRoleFromCollection(collectionId, roleDto.getId());

    // then
    final SimpleCollectionDefinitionDto collection = getCollection(collectionId);
    assertThat(collection.getData().getRoles().size(), is(1));
    assertThat(collection.getData().getRoles(), not(hasItem(roleDto)));
  }

  @Test
  public void deletingLastManagerFails() {
    // given
    final String collectionId = createNewCollection();
    final SimpleCollectionDefinitionDto expectedCollection = getCollection(collectionId);
    final CollectionRoleDto roleEntryDto = expectedCollection.getData().getRoles().get(0);

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDeleteRoleToCollectionRequest(collectionId, roleEntryDto.getId())
      .execute();

    // then
    assertThat(response.getStatus(), is(HttpStatus.SC_CONFLICT));

    final SimpleCollectionDefinitionDto collection = getCollection(collectionId);
    assertThat(collection, is(expectedCollection));
  }

  private IdDto addRoleToCollection(final String collectionId, final CollectionRoleDto roleDto) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildAddRoleToCollectionRequest(collectionId, roleDto)
      .execute(IdDto.class, 200);
  }

  private void updateRoleOnCollection(final String collectionId,
                                      final String roleEntryId,
                                      final CollectionRoleUpdateDto updateDto) {
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateRoleToCollectionRequest(collectionId, roleEntryId, updateDto)
      .execute();
    assertThat(response.getStatus(), is(204));
  }

  private void deleteRoleFromCollection(final String collectionId,
                                        final String roleEntryId) {
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDeleteRoleToCollectionRequest(collectionId, roleEntryId)
      .execute();
    assertThat(response.getStatus(), is(204));
  }

  private SimpleCollectionDefinitionDto getCollection(final String id) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetCollectionRequest(id)
      .execute(SimpleCollectionDefinitionDto.class, 200);
  }

  private String createNewCollection() {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateCollectionRequest()
      .execute(IdDto.class, 200)
      .getId();
  }
}
