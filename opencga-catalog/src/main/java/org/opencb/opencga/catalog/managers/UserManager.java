/*
 * Copyright 2015-2017 OpenCB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opencb.opencga.catalog.managers;

import org.apache.commons.lang3.StringUtils;
import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.commons.datastore.core.result.Error;
import org.opencb.commons.utils.ListUtils;
import org.opencb.opencga.catalog.audit.AuditManager;
import org.opencb.opencga.catalog.audit.AuditRecord;
import org.opencb.opencga.catalog.auth.authentication.AuthenticationManager;
import org.opencb.opencga.catalog.auth.authentication.AzureADAuthenticationManager;
import org.opencb.opencga.catalog.auth.authentication.CatalogAuthenticationManager;
import org.opencb.opencga.catalog.auth.authentication.LDAPAuthenticationManager;
import org.opencb.opencga.catalog.auth.authorization.AuthorizationManager;
import org.opencb.opencga.catalog.db.DBAdaptorFactory;
import org.opencb.opencga.catalog.db.api.UserDBAdaptor;
import org.opencb.opencga.catalog.exceptions.*;
import org.opencb.opencga.catalog.io.CatalogIOManagerFactory;
import org.opencb.opencga.catalog.utils.ParamUtils;
import org.opencb.opencga.catalog.utils.UUIDUtils;
import org.opencb.opencga.core.config.AuthenticationOrigin;
import org.opencb.opencga.core.config.Configuration;
import org.opencb.opencga.core.models.common.Enums;
import org.opencb.opencga.core.models.file.File;
import org.opencb.opencga.core.models.project.Project;
import org.opencb.opencga.core.models.study.Group;
import org.opencb.opencga.core.models.study.GroupUpdateParams;
import org.opencb.opencga.core.models.study.Study;
import org.opencb.opencga.core.models.user.Account;
import org.opencb.opencga.core.models.user.User;
import org.opencb.opencga.core.response.OpenCGAResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
public class UserManager extends AbstractManager {

    private String INTERNAL_AUTHORIZATION = CatalogAuthenticationManager.INTERNAL;
    private Map<String, AuthenticationManager> authenticationManagerMap;

    protected static final String EMAIL_PATTERN = "^['_A-Za-z0-9-\\+]+(\\.['_A-Za-z0-9-]+)*@"
            + "[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$";
    protected static final Pattern EMAILPATTERN = Pattern.compile(EMAIL_PATTERN);
    protected static Logger logger = LoggerFactory.getLogger(UserManager.class);

    UserManager(AuthorizationManager authorizationManager, AuditManager auditManager, CatalogManager catalogManager,
                DBAdaptorFactory catalogDBAdaptorFactory, CatalogIOManagerFactory ioManagerFactory,
                Configuration configuration) throws CatalogException {
        super(authorizationManager, auditManager, catalogManager, catalogDBAdaptorFactory, ioManagerFactory, configuration);

        String secretKey = configuration.getAdmin().getSecretKey();
        long expiration = configuration.getAuthentication().getExpiration();

        authenticationManagerMap = new LinkedHashMap<>();
        if (configuration.getAuthentication().getAuthenticationOrigins() != null) {
            for (AuthenticationOrigin authenticationOrigin : configuration.getAuthentication().getAuthenticationOrigins()) {
                if (authenticationOrigin.getId() != null) {
                    switch (authenticationOrigin.getType()) {
                        case LDAP:
                            authenticationManagerMap.put(authenticationOrigin.getId(),
                                    new LDAPAuthenticationManager(authenticationOrigin, secretKey, expiration));
                            break;
                        case AzureAD:
                            authenticationManagerMap.put(authenticationOrigin.getId(),
                                    new AzureADAuthenticationManager(authenticationOrigin));
                            break;
                        default:
                            break;
                    }
                }
            }
        }
        // Even if internal authentication is not present in the configuration file, create it
        authenticationManagerMap.putIfAbsent(INTERNAL_AUTHORIZATION,
                new CatalogAuthenticationManager(catalogDBAdaptorFactory, configuration.getEmail(), secretKey, expiration));
        AuthenticationOrigin authenticationOrigin = new AuthenticationOrigin();
        if (configuration.getAuthentication().getAuthenticationOrigins() == null) {
            configuration.getAuthentication().setAuthenticationOrigins(Arrays.asList(authenticationOrigin));
        } else {
            // Check if OPENCGA authentication is already present in catalog configuration
            boolean catalogPresent = false;
            for (AuthenticationOrigin origin : configuration.getAuthentication().getAuthenticationOrigins()) {
                if (AuthenticationOrigin.AuthenticationType.OPENCGA == origin.getType()) {
                    catalogPresent = true;
                    break;
                }
            }
            if (!catalogPresent) {
                List<AuthenticationOrigin> linkedList = new LinkedList<>();
                linkedList.addAll(configuration.getAuthentication().getAuthenticationOrigins());
                linkedList.add(authenticationOrigin);
                configuration.getAuthentication().setAuthenticationOrigins(linkedList);
            }
        }
    }

    static void checkEmail(String email) throws CatalogParameterException {
        if (email == null || !EMAILPATTERN.matcher(email).matches()) {
            throw new CatalogParameterException("Email '" + email + "' not valid");
        }
    }

    /**
     * Get the userId from the sessionId.
     *
     * @param token Token
     * @return UserId owner of the sessionId. Empty string if SessionId does not match.
     * @throws CatalogException when the session id does not correspond to any user or the token has expired.
     */
    public String getUserId(String token) throws CatalogException {
        for (Map.Entry<String, AuthenticationManager> entry : authenticationManagerMap.entrySet()) {
            AuthenticationManager authenticationManager = entry.getValue();
            try {
                String userId = authenticationManager.getUserId(token);
                userDBAdaptor.checkId(userId);
                return userId;
            } catch (Exception e) {
                logger.debug("Could not get user from token using {} authentication manager. {}", entry.getKey(), e.getMessage(), e);
            }
        }
        // We make this call again to get the original exception
        return authenticationManagerMap.get(INTERNAL_AUTHORIZATION).getUserId(token);
    }

    public void changePassword(String userId, String oldPassword, String newPassword) throws CatalogException {
        ParamUtils.checkParameter(userId, "userId");
//        checkParameter(sessionId, "sessionId");
        ParamUtils.checkParameter(oldPassword, "oldPassword");
        ParamUtils.checkParameter(newPassword, "newPassword");
        try {
            if (oldPassword.equals(newPassword)) {
                throw new CatalogException("New password is the same as the old password.");
            }

            userDBAdaptor.checkId(userId);
            String authOrigin = getAuthenticationOriginId(userId);
            authenticationManagerMap.get(authOrigin).changePassword(userId, oldPassword, newPassword);
            userDBAdaptor.updateUserLastModified(userId);
            auditManager.auditUser(userId, Enums.Action.CHANGE_USER_PASSWORD, userId,
                    new AuditRecord.Status(AuditRecord.Status.Result.SUCCESS));
        } catch (CatalogException e) {
            auditManager.auditUser(userId, Enums.Action.CHANGE_USER_PASSWORD, userId,
                    new AuditRecord.Status(AuditRecord.Status.Result.ERROR, e.getError()));
            throw e;
        }
    }

    public OpenCGAResult<User> create(User user, @Nullable String token) throws CatalogException {
        // Check if the users can be registered publicly or just the admin.
        ObjectMap auditParams = new ObjectMap("user", user);

        String userId = user.getId();
        // We add a condition to check if the registration is private + user (or system) is not trying to create the ADMINISTRATOR user
        if (!authorizationManager.isPublicRegistration() && !OPENCGA.equals(user.getId())) {
            userId = authenticationManagerMap.get(INTERNAL_AUTHORIZATION).getUserId(token);
            if (!OPENCGA.equals(userId)) {
                String errorMsg = "The registration is closed to the public: Please talk to your administrator.";
                auditManager.auditCreate(userId, Enums.Resource.USER, user.getId(), "", "", "", auditParams,
                        new AuditRecord.Status(AuditRecord.Status.Result.ERROR, new Error(0, "", errorMsg)));
                throw new CatalogException(errorMsg);
            }
        }

        ParamUtils.checkObj(user, "User");
        ParamUtils.checkValidUserId(user.getId());
        ParamUtils.checkParameter(user.getName(), "name");
        checkEmail(user.getEmail());
        ParamUtils.checkObj(user.getAccount(), "account");
        user.setOrganization(ParamUtils.defaultObject(user.getOrganization(), ""));

        String password = "";
        if (StringUtils.isEmpty(user.getPassword())) {
            // The authentication origin must be different than internal
            Set<String> authOrigins = configuration.getAuthentication().getAuthenticationOrigins()
                    .stream()
                    .map(AuthenticationOrigin::getId)
                    .collect(Collectors.toSet());
            if (!authOrigins.contains(user.getAccount().getAuthentication().getId())) {
                throw new CatalogException("Unknown authentication origin id '" + user.getAccount().getAuthentication() + "'");
            }
        } else {
            password = user.getPassword();
            user.setPassword("");

            user.getAccount().setAuthentication(new Account.AuthenticationOrigin(INTERNAL_AUTHORIZATION, false));
        }

        checkUserExists(user.getId());
        user.setStatus(new User.UserStatus());

        if (user.getAccount().getType() == null) {
            user.getAccount().setType(Account.Type.GUEST);
        }

        if (user.getQuota() <= 0L) {
            user.setQuota(-1L);
        }

        try {
            if (user.getProjects() != null && !user.getProjects().isEmpty()) {
                throw new CatalogException("Creating user and projects in a single transaction is forbidden");
            }

            catalogIOManagerFactory.getDefault().createUser(user.getId());
            userDBAdaptor.insert(user, QueryOptions.empty());

            auditManager.auditCreate(userId, Enums.Resource.USER, user.getId(), "", "", "", auditParams,
                    new AuditRecord.Status(AuditRecord.Status.Result.SUCCESS));

            OpenCGAResult<User> queryResult = userDBAdaptor.get(user.getId(), QueryOptions.empty(), null);

            if (StringUtils.isNotEmpty(password)) {
                authenticationManagerMap.get(INTERNAL_AUTHORIZATION).newPassword(user.getId(), password);
            }

            return queryResult;
        } catch (CatalogIOException | CatalogDBException e) {
            if (!userDBAdaptor.exists(user.getId())) {
                logger.error("ERROR! DELETING USER! " + user.getId());
                catalogIOManagerFactory.getDefault().deleteUser(user.getId());
            }

            auditManager.auditCreate(userId, Enums.Resource.USER, user.getId(), "", "", "", auditParams,
                    new AuditRecord.Status(AuditRecord.Status.Result.ERROR, e.getError()));

            throw e;
        }
    }

    /**
     * Create a new user.
     *
     * @param id           User id
     * @param name         Name
     * @param email        Email
     * @param password     Encrypted Password
     * @param organization Optional organization
     * @param quota        Maximum user disk quota
     * @param type  User account type. Full or guest.
     * @param token        JWT token.
     * @return The created user
     * @throws CatalogException If user already exists, or unable to create a new user.
     */
    public OpenCGAResult<User> create(String id, String name, String email, String password, String organization, Long quota,
                                      Account.Type type, String token) throws CatalogException {
        User user = new User(id, name, email, password, organization, User.UserStatus.READY)
                .setAccount(new Account(type, "", "", null))
                .setQuota(quota != null ? quota : 0L);

        return create(user, token);
    }

    public void syncAllUsersOfExternalGroup(String study, String authOrigin, String token) throws CatalogException {
        if (!OPENCGA.equals(authenticationManagerMap.get(INTERNAL_AUTHORIZATION).getUserId(token))) {
            throw new CatalogAuthorizationException("Only the root user can perform this action");
        }

        OpenCGAResult<Group> allGroups = catalogManager.getStudyManager().getGroup(study, null, token);

        boolean foundAny = false;
        for (Group group : allGroups.getResults()) {
            if (group.getSyncedFrom() != null && group.getSyncedFrom().getAuthOrigin().equals(authOrigin)) {
                logger.info("Fetching users of group '{}' from authentication origin '{}'", group.getSyncedFrom().getRemoteGroup(),
                        group.getSyncedFrom().getAuthOrigin());
                foundAny = true;

                List<User> userList;
                try {
                    userList = authenticationManagerMap.get(group.getSyncedFrom().getAuthOrigin())
                            .getUsersFromRemoteGroup(group.getSyncedFrom().getRemoteGroup());
                } catch (CatalogException e) {
                    // There was some kind of issue for which we could not retrieve the group information.
                    logger.info("Removing all users from group '{}' belonging to group '{}' in the external authentication origin",
                            group.getId(), group.getSyncedFrom().getAuthOrigin());
                    logger.info("Please, manually remove group '{}' if external group '{}' was removed from the authentication origin",
                            group.getId(), group.getSyncedFrom().getAuthOrigin());
                    catalogManager.getStudyManager().updateGroup(study, group.getId(), ParamUtils.UpdateAction.SET,
                            new GroupUpdateParams(Collections.emptyList()), token);
                    continue;
                }
                Iterator<User> iterator = userList.iterator();
                while (iterator.hasNext()) {
                    User user = iterator.next();
                    try {
                        create(user, token);
                        logger.info("User '{}' ({}) successfully created", user.getId(), user.getName());
                    } catch (CatalogParameterException e) {
                        logger.warn("Could not create user '{}' ({}). {}", user.getId(), user.getName(), e.getMessage());
                        iterator.remove();
                    } catch (CatalogException e) {
                        if (!e.getMessage().contains("already exists")) {
                            logger.warn("Could not create user '{}' ({}). {}", user.getId(), user.getName(), e.getMessage());
                            iterator.remove();
                        }
                    }
                }

                GroupUpdateParams updateParams;
                if (ListUtils.isEmpty(userList)) {
                    logger.info("No members associated to the external group");
                    updateParams = new GroupUpdateParams(Collections.emptyList());
                } else {
                    logger.info("Associating members to the internal OpenCGA group");
                    updateParams = new GroupUpdateParams(new ArrayList<>(userList.stream().map(User::getId).collect(Collectors.toSet())));
                }
                catalogManager.getStudyManager().updateGroup(study, group.getId(), ParamUtils.UpdateAction.SET, updateParams, token);
            }
        }
        if (!foundAny) {
            logger.info("No synced groups found in study '{}' from authentication origin '{}'", study, authOrigin);
        }
    }

    /**
     * Register all the users belonging to a remote group. If internalGroup and study are not null, it will also associate the remote group
     * to the internalGroup defined.
     *
     * @param authOrigin Authentication origin.
     * @param remoteGroup Group name of the remote authentication origin.
     * @param internalGroup Group name in Catalog that will be associated to the remote group.
     * @param study Study where the internal group will be associated.
     * @param sync Boolean indicating whether the remote group will be synced with the internal group or not.
     * @param token JWT token. The token should belong to the root user.
     * @throws CatalogException If any of the parameters is wrong or there is any internal error.
     */
    public void importRemoteGroupOfUsers(String authOrigin, String remoteGroup, @Nullable String internalGroup, @Nullable String study,
                                         boolean sync, String token) throws CatalogException {
        String userId = getUserId(token);

        ObjectMap auditParams = new ObjectMap()
                .append("authOrigin", authOrigin)
                .append("remoteGroup", remoteGroup)
                .append("internalGroup", internalGroup)
                .append("study", study)
                .append("sync", sync)
                .append("token", token);
        try {
            if (!OPENCGA.equals(authenticationManagerMap.get(INTERNAL_AUTHORIZATION).getUserId(token))) {
                throw new CatalogAuthorizationException("Only the root user can perform this action");
            }

            ParamUtils.checkParameter(authOrigin, "Authentication origin");
            ParamUtils.checkParameter(remoteGroup, "Remote group");

            if (!authenticationManagerMap.containsKey(authOrigin)) {
                throw new CatalogException("Unknown authentication origin");
            }

            List<User> userList;
            if (sync) {
                // We don't create any user as they will be automatically populated during login
                userList = Collections.emptyList();
            } else {
                logger.info("Fetching users from authentication origin '{}'", authOrigin);

                // Register the users
                userList = authenticationManagerMap.get(authOrigin).getUsersFromRemoteGroup(remoteGroup);
                for (User user : userList) {
                    try {
                        create(user, token);
                        logger.info("User '{}' successfully created", user.getId());
                    } catch (CatalogException e) {
                        logger.warn("{}", e.getMessage());
                    }
                }
            }

            if (StringUtils.isNotEmpty(internalGroup) && StringUtils.isNotEmpty(study)) {
                // Check if the group already exists
                OpenCGAResult<Group> groupResult = catalogManager.getStudyManager().getGroup(study, internalGroup, token);
                if (groupResult.getNumResults() == 1) {
                    logger.error("Cannot synchronise with group {}. The group already exists and is already in use.", internalGroup);
                    throw new CatalogException("Cannot synchronise with group " +  internalGroup
                            + ". The group already exists and is already in use.");
                }

                // Create new group associating it to the remote group
                try {
                    logger.info("Attempting to register group '{}' in study '{}'", internalGroup, study);
                    Group.Sync groupSync = null;
                    if (sync) {
                        groupSync = new Group.Sync(authOrigin, remoteGroup);
                    }
                    Group group = new Group(internalGroup, userList.stream().map(User::getId).collect(Collectors.toList()))
                            .setSyncedFrom(groupSync);
                    catalogManager.getStudyManager().createGroup(study, group, token);
                    logger.info("Group '{}' created and synchronised with external group", internalGroup);
                    auditManager.audit(userId, Enums.Action.IMPORT_EXTERNAL_GROUP_OF_USERS, Enums.Resource.USER, group.getId(),
                            "", study, "", auditParams, new AuditRecord.Status(AuditRecord.Status.Result.SUCCESS));
                } catch (CatalogException e) {
                    logger.error("Could not register group '{}' in study '{}'\n{}", internalGroup, study, e.getMessage(), e);
                    throw new CatalogException("Could not register group '" + internalGroup + "' in study '" + study + "': "
                            + e.getMessage(), e);
                }
            }
        } catch (CatalogException e) {
            auditManager.audit(userId, Enums.Action.IMPORT_EXTERNAL_GROUP_OF_USERS, Enums.Resource.USER, "", "", "", "",
                    auditParams, new AuditRecord.Status(AuditRecord.Status.Result.ERROR, e.getError()));
            throw e;
        }
    }

    /**
     * Register all the ids. If internalGroup and study are not null, it will also associate the users to the internalGroup defined.
     *
     * @param authOrigin Authentication origin.
     * @param idList List of entity ids existing in the authentication origin.
     * @param isApplication boolean indicating whether the id list belong to external applications or users.
     * @param internalGroup Group name in Catalog that will be associated to the remote group.
     * @param study Study where the internal group will be associated.
     * @param token JWT token. The token should belong to the root user.
     * @throws CatalogException If any of the parameters is wrong or there is any internal error.
     */
    public void importRemoteEntities(String authOrigin, List<String> idList, boolean isApplication, @Nullable String internalGroup,
                                     @Nullable String study, String token) throws CatalogException {
        ObjectMap auditParams = new ObjectMap()
                .append("authOrigin", authOrigin)
                .append("idList", idList)
                .append("isApplication", isApplication)
                .append("internalGroup", internalGroup)
                .append("study", study)
                .append("token", token);

        String userId = getUserId(token);

        try {
            if (!OPENCGA.equals(userId)) {
                throw new CatalogAuthorizationException("Only the root user can perform this action");
            }

            ParamUtils.checkParameter(authOrigin, "Authentication origin");
            ParamUtils.checkObj(idList, "ids");

            if (!authenticationManagerMap.containsKey(authOrigin)) {
                throw new CatalogException("Unknown authentication origin");
            }

            if (!isApplication) {
                logger.info("Fetching user information from authentication origin '{}'", authOrigin);
                List<User> parsedUserList = authenticationManagerMap.get(authOrigin).getRemoteUserInformation(idList);
                for (User user : parsedUserList) {
                    create(user, token);
                    auditManager.audit(userId, Enums.Action.IMPORT_EXTERNAL_USERS, Enums.Resource.USER, user.getId(), "", "",
                            "", auditParams, new AuditRecord.Status(AuditRecord.Status.Result.SUCCESS));
                    logger.info("User '{}' successfully created", user.getId());
                }
            } else {
                for (String applicationId : idList) {
                    User application = new User(applicationId, new Account()
                            .setType(Account.Type.GUEST)
                            .setAuthentication(new Account.AuthenticationOrigin(authOrigin, true)))
                            .setEmail("mail@mail.co.uk");
                    create(application, token);
                    auditManager.audit(userId, Enums.Action.IMPORT_EXTERNAL_USERS, Enums.Resource.USER, application.getId(), "",
                            "", "", auditParams, new AuditRecord.Status(AuditRecord.Status.Result.SUCCESS));
                    logger.info("User (application) '{}' successfully created", application.getId());
                }
            }

            if (StringUtils.isNotEmpty(internalGroup) && StringUtils.isNotEmpty(study)) {
                // Check if the group already exists
                try {
                    OpenCGAResult<Group> group = catalogManager.getStudyManager().getGroup(study, internalGroup, token);
                    if (group.getNumResults() == 1) {
                        // We will add those users to the existing group
                        catalogManager.getStudyManager().updateGroup(study, internalGroup, ParamUtils.UpdateAction.ADD,
                                new GroupUpdateParams(idList), token);
                        return;
                    }
                } catch (CatalogException e) {
                    logger.warn("The group '{}' did not exist.", internalGroup);
                }

                // Create new group associating it to the remote group
                try {
                    logger.info("Attempting to register group '{}' in study '{}'", internalGroup, study);
                    Group group = new Group(internalGroup, idList);
                    catalogManager.getStudyManager().createGroup(study, group, token);
                } catch (CatalogException e) {
                    logger.error("Could not register group '{}' in study '{}'\n{}", internalGroup, study, e.getMessage());
                }
            }
        } catch (CatalogException e) {
            auditManager.audit(userId, Enums.Action.IMPORT_EXTERNAL_USERS, Enums.Resource.USER, "", "", "", "", auditParams,
                    new AuditRecord.Status(AuditRecord.Status.Result.ERROR, e.getError()));
            throw e;
        }
    }

    /**
     * Reads a user from Catalog given a user id.
     *
     * @param userId    user id of the object to read
     * @param options   Read options
     * @param sessionId sessionId
     * @return The specified object
     * @throws CatalogException CatalogException
     */
    public OpenCGAResult<User> get(String userId, QueryOptions options, String sessionId) throws CatalogException {
        return get(userId, null, options, sessionId);
    }

    /**
     * Gets the user information.
     *
     * @param userId       User id
     * @param lastModified If lastModified matches with the one in Catalog, return an empty OpenCGAResult.
     * @param options      QueryOptions
     * @param token    SessionId of the user performing this operation.
     * @return The requested user
     * @throws CatalogException CatalogException
     */
    public OpenCGAResult<User> get(String userId, String lastModified, QueryOptions options, String token)
            throws CatalogException {
        ParamUtils.checkParameter(userId, "userId");
        ParamUtils.checkParameter(token, "sessionId");
        options = ParamUtils.defaultObject(options, QueryOptions::new);

        ObjectMap auditParams = new ObjectMap()
                .append("userId", userId)
                .append("lastModified", lastModified)
                .append("options", options)
                .append("token", token);
        try {
            validateUserAndToken(userId, token);
            OpenCGAResult<User> userDataResult = userDBAdaptor.get(userId, options, lastModified);

            // Remove some unnecessary and prohibited parameters
            for (User user : userDataResult.getResults()) {
                user.setPassword(null);
                if (user.getProjects() != null) {
                    for (Project project : user.getProjects()) {
                        if (project.getStudies() != null) {
                            for (Study study : project.getStudies()) {
                                study.setVariableSets(null);
                            }
                        }
                    }
                }
            }

            auditManager.auditInfo(userId, Enums.Resource.USER, userId, "", "", "", auditParams,
                    new AuditRecord.Status(AuditRecord.Status.Result.SUCCESS));
            return userDataResult;
        } catch (CatalogException e) {
            auditManager.auditInfo(userId, Enums.Resource.USER, userId, "", "", "", auditParams,
                    new AuditRecord.Status(AuditRecord.Status.Result.ERROR, e.getError()));
            throw e;
        }
    }

    public OpenCGAResult<User> update(String userId, ObjectMap parameters, QueryOptions options, String token) throws CatalogException {
        String loggedUser = getUserId(token);

        ObjectMap auditParams = new ObjectMap()
                .append("userId", userId)
                .append("updateParams", parameters)
                .append("options", options)
                .append("token", token);
        try {
            ParamUtils.checkParameter(userId, "userId");
            ParamUtils.checkObj(parameters, "parameters");
            ParamUtils.checkParameter(token, "token");

            validateUserAndToken(userId, token);
            for (String s : parameters.keySet()) {
                if (!s.matches("name|email|organization|attributes")) {
                    throw new CatalogDBException("Parameter '" + s + "' can't be changed");
                }
            }

            if (parameters.containsKey("email")) {
                checkEmail(parameters.getString("email"));
            }
            userDBAdaptor.updateUserLastModified(userId);
            OpenCGAResult result = userDBAdaptor.update(userId, parameters);
            auditManager.auditUpdate(loggedUser, Enums.Resource.USER, userId, "", "", "", auditParams,
                    new AuditRecord.Status(AuditRecord.Status.Result.SUCCESS));

            OpenCGAResult<User> queryResult = userDBAdaptor.get(userId, new QueryOptions(QueryOptions.INCLUDE, parameters.keySet()), "");
            queryResult.setTime(queryResult.getTime() + result.getTime());

            return queryResult;
        } catch (CatalogException e) {
            auditManager.auditUpdate(loggedUser, Enums.Resource.USER, userId, "", "", "", auditParams,
                    new AuditRecord.Status(AuditRecord.Status.Result.ERROR, e.getError()));
            throw e;
        }
    }

    /**
     * Delete entries from Catalog.
     *
     * @param userIdList Comma separated list of ids corresponding to the objects to delete
     * @param options    Deleting options.
     * @param token      Token
     * @return A list with the deleted objects
     * @throws CatalogException CatalogException.
     */
    public OpenCGAResult<User> delete(String userIdList, QueryOptions options, String token) throws CatalogException {
        ParamUtils.checkParameter(userIdList, "userIdList");
        ParamUtils.checkParameter(token, "token");

        String operationUuid = UUIDUtils.generateOpenCGAUUID(UUIDUtils.Entity.AUDIT);
        ObjectMap auditParams = new ObjectMap()
                .append("userIdList", userIdList)
                .append("options", options)
                .append("token", token);

        String tokenUser = getUserId(token);

        List<String> userIds = Arrays.asList(userIdList.split(","));
        OpenCGAResult<User> deletedUsers = OpenCGAResult.empty();
        for (String userId : userIds) {
            // Only if the user asking the deletion is the ADMINISTRATOR or the user to be deleted itself...
            if (OPENCGA.equals(tokenUser) || userId.equals(tokenUser)) {
                try {
                    OpenCGAResult result = userDBAdaptor.delete(userId, options);

                    auditManager.auditDelete(operationUuid, tokenUser, Enums.Resource.USER, userId, "", "", "", auditParams,
                            new AuditRecord.Status(AuditRecord.Status.Result.SUCCESS));

                    Query query = new Query()
                            .append(UserDBAdaptor.QueryParams.ID.key(), userId)
                            .append(UserDBAdaptor.QueryParams.STATUS_NAME.key(), User.UserStatus.DELETED);
                    OpenCGAResult<User> deletedUser = userDBAdaptor.get(query, QueryOptions.empty());
                    deletedUser.setTime(deletedUser.getTime() + result.getTime());

                    deletedUsers.append(deletedUser);
                } catch (CatalogException e) {
                    auditManager.auditDelete(operationUuid, tokenUser, Enums.Resource.USER, userId, "", "", "", auditParams,
                            new AuditRecord.Status(AuditRecord.Status.Result.ERROR, e.getError()));
                }
            }
        }
        return deletedUsers;
    }

    /**
     * Delete the entries satisfying the query.
     *
     * @param query     Query of the objects to be deleted.
     * @param options   Deleting options.
     * @param sessionId sessionId.
     * @return A list with the deleted objects.
     * @throws CatalogException CatalogException
     * @throws IOException      IOException.
     */
    public OpenCGAResult<User> delete(Query query, QueryOptions options, String sessionId) throws CatalogException, IOException {
        QueryOptions queryOptions = new QueryOptions(QueryOptions.INCLUDE, UserDBAdaptor.QueryParams.ID.key());
        OpenCGAResult<User> userDataResult = userDBAdaptor.get(query, queryOptions);
        List<String> userIds = userDataResult.getResults().stream().map(User::getId).collect(Collectors.toList());
        String userIdStr = StringUtils.join(userIds, ",");
        return delete(userIdStr, options, sessionId);
    }

    public OpenCGAResult<User> restore(String ids, QueryOptions options, String sessionId) throws CatalogException {
        throw new UnsupportedOperationException();
    }

    public OpenCGAResult resetPassword(String userId, String sessionId) throws CatalogException {
        ParamUtils.checkParameter(userId, "userId");
        ParamUtils.checkParameter(sessionId, "sessionId");
        try {
            validateUserAndToken(userId, sessionId);
            String authOrigin = getAuthenticationOriginId(userId);
            OpenCGAResult writeResult = authenticationManagerMap.get(authOrigin).resetPassword(userId);
            auditManager.auditUser(userId, Enums.Action.RESET_USER_PASSWORD, userId,
                    new AuditRecord.Status(AuditRecord.Status.Result.SUCCESS));
            return writeResult;
        } catch (CatalogException e) {
            auditManager.auditUser(userId, Enums.Action.RESET_USER_PASSWORD, userId,
                    new AuditRecord.Status(AuditRecord.Status.Result.ERROR, e.getError()));
            throw e;
        }
    }

    public String loginAsAdmin(String password) throws CatalogException {
        return login(OPENCGA, password);
    }

    public String login(String username, String password) throws CatalogException {
        ParamUtils.checkParameter(username, "userId");
        ParamUtils.checkParameter(password, "password");

        String authId = null;
        String token = null;

        // We attempt to login the user with the different authentication managers
        for (Map.Entry<String, AuthenticationManager> entry : authenticationManagerMap.entrySet()) {
            AuthenticationManager authenticationManager = entry.getValue();
            try {
                token = authenticationManager.authenticate(username, password);
                authId = entry.getKey();
                break;
            } catch (CatalogAuthenticationException e) {
                logger.debug("Attempted authentication failed with {} for user '{}'\n{}", entry.getKey(), username, e.getMessage(), e);
            }
        }

        if (token == null) {
            // TODO: We should raise better exceptions. It could fail for other reasons.
            auditManager.auditUser(username, Enums.Action.LOGIN, username,
                    new AuditRecord.Status(AuditRecord.Status.Result.ERROR, new Error(0, "", "Incorrect user or password.")));
            throw CatalogAuthenticationException.incorrectUserOrPassword();
        }

        auditManager.auditUser(username, Enums.Action.LOGIN, username, new AuditRecord.Status(AuditRecord.Status.Result.SUCCESS));
        String userId = authenticationManagerMap.get(authId).getUserId(token);
        if (!INTERNAL_AUTHORIZATION.equals(authId)) {
            // External authorization
            try {
                // If the user is not registered, an exception will be raised
                userDBAdaptor.checkId(userId);
            } catch (CatalogDBException e) {
                // The user does not exist so we register it
                User user = authenticationManagerMap.get(authId).getRemoteUserInformation(Collections.singletonList(userId)).get(0);
                // Generate a root token to be able to create the user even if the installation is private
                String rootToken = authenticationManagerMap.get(INTERNAL_AUTHORIZATION).createToken(OPENCGA);
                create(user, rootToken);
            }

            try {
                List<String> remoteGroups = authenticationManagerMap.get(authId).getRemoteGroups(token);

                // Resync synced groups of user in OpenCGA
                studyDBAdaptor.resyncUserWithSyncedGroups(userId, remoteGroups, authId);
            } catch (CatalogException e) {
                logger.error("Could not update synced groups for user '" + userId + "'\n" + e.getMessage(), e);
            }
        }

        return token;
    }

    /**
     * Create a new token if the token provided corresponds to the user and it is not expired yet.
     *
     * @param userId user id to whom the token belongs to.
     * @param token  active token.
     * @return a new token with the default expiration updated.
     * @throws CatalogException if the token does not correspond to the user or the token is expired.
     */
    public String refreshToken(String userId, String token) throws CatalogException {
        String authenticatedUser = userId;
        try {
            authenticatedUser = authenticationManagerMap.get(INTERNAL_AUTHORIZATION).getUserId(token);
            if (!userId.equals(authenticatedUser)) {
                throw new CatalogException("Cannot refresh token. The token received does not correspond to " + userId);
            }
            String newToken = authenticationManagerMap.get(INTERNAL_AUTHORIZATION).createToken(userId);

            auditManager.auditUser(authenticatedUser, Enums.Action.LOGIN, userId,
                    new AuditRecord.Status(AuditRecord.Status.Result.SUCCESS));
            return newToken;
        } catch (CatalogException e) {
            auditManager.auditUser(authenticatedUser, Enums.Action.LOGIN, userId,
                    new AuditRecord.Status(AuditRecord.Status.Result.ERROR, e.getError()));
            throw e;
        }
    }

    /**
     * This method will be only callable by the system. It generates a new session id for the user.
     *
     * @param userId           user id for which a session will be generated.
     * @param adminCredentials Password or active session of the OpenCGA admin.
     * @return an objectMap containing the new sessionId
     * @throws CatalogException if the password is not correct or the userId does not exist.
     */
    public String getNonExpiringToken(String userId, String adminCredentials) throws CatalogException {
        validateUserAndToken(OPENCGA, adminCredentials);
        return authenticationManagerMap.get(INTERNAL_AUTHORIZATION).createNonExpiringToken(userId);
    }

    public String getAdminNonExpiringToken(String adminCredentials) throws CatalogException {
        return getNonExpiringToken(OPENCGA, adminCredentials);
    }

    /**
     * Add a new filter to the user account.
     * <p>
     * @param userId       user id to whom the filter will be associated.
     * @param name         Filter name.
     * @param description  Filter description.
     * @param bioformat    Bioformat where the filter should be applied.
     * @param query        Query object.
     * @param queryOptions Query options object.
     * @param token    session id of the user asking to store the filter.
     * @return the created filter.
     * @throws CatalogException if there already exists a filter with that same name for the user or if the user corresponding to the
     *                          session id is not the same as the provided user id.
     */
    public OpenCGAResult<User.Filter> addFilter(String userId, String name, String description, File.Bioformat bioformat, Query query,
                                                QueryOptions queryOptions, String token) throws CatalogException {
        ParamUtils.checkParameter(userId, "userId");
        ParamUtils.checkParameter(token, "sessionId");
        ParamUtils.checkParameter(name, "name");
        ParamUtils.checkObj(bioformat, "bioformat");
        ParamUtils.checkObj(query, "Query");
        ParamUtils.checkObj(queryOptions, "QueryOptions");
        if (description == null) {
            description = "";
        }

        String userIdAux = getUserId(token);
        ObjectMap auditParams = new ObjectMap()
                .append("userId", userId)
                .append("name", name)
                .append("description", description)
                .append("bioformat", bioformat)
                .append("query", query)
                .append("queryOptions", queryOptions)
                .append("token", token);
        try {
            userDBAdaptor.checkId(userId);
            if (!userId.equals(userIdAux)) {
                throw new CatalogException("User " + userIdAux + " is not authorised to store filters for user " + userId);
            }

            Query queryExists = new Query()
                    .append(UserDBAdaptor.QueryParams.ID.key(), userId)
                    .append(UserDBAdaptor.QueryParams.CONFIGS_FILTERS_NAME.key(), name);
            if (userDBAdaptor.count(queryExists).getNumMatches() > 0) {
                throw new CatalogException("There already exists a filter called " + name + " for user " + userId);
            }

            User.Filter filter = new User.Filter(name, description, bioformat, query, queryOptions);
            OpenCGAResult result = userDBAdaptor.addFilter(userId, filter);
            auditManager.auditUser(userIdAux, Enums.Action.CHANGE_USER_CONFIG, userId, auditParams,
                    new AuditRecord.Status(AuditRecord.Status.Result.SUCCESS));
            return new OpenCGAResult<>(result.getTime(), Collections.emptyList(), 1, Collections.singletonList(filter), 1);
        } catch (CatalogException e) {
            auditManager.auditUser(userIdAux, Enums.Action.CHANGE_USER_CONFIG, userId, auditParams,
                    new AuditRecord.Status(AuditRecord.Status.Result.ERROR, e.getError()));
            throw e;
        }
    }

    /**
     * Update the filter information.
     * <p>
     * @param userId    user id to whom the filter should be updated.
     * @param name      Filter name.
     * @param params    Map containing the parameters to be updated.
     * @param token session id of the user asking to update the filter.
     * @return the updated filter.
     * @throws CatalogException if the filter could not be updated because the filter name is not correct or if the user corresponding to
     *                          the session id is not the same as the provided user id.
     */
    public OpenCGAResult<User.Filter> updateFilter(String userId, String name, ObjectMap params, String token) throws CatalogException {
        ParamUtils.checkParameter(userId, "userId");
        ParamUtils.checkParameter(token, "token");
        ParamUtils.checkParameter(name, "name");

        String userIdAux = getUserId(token);
        ObjectMap auditParams = new ObjectMap()
                .append("userId", userId)
                .append("name", name)
                .append("params", params)
                .append("token", token);
        try {
            userDBAdaptor.checkId(userId);
            if (!userId.equals(userIdAux)) {
                throw new CatalogException("User " + userIdAux + " is not authorised to update filters for user " + userId);
            }

            Query queryExists = new Query()
                    .append(UserDBAdaptor.QueryParams.ID.key(), userId)
                    .append(UserDBAdaptor.QueryParams.CONFIGS_FILTERS_NAME.key(), name);
            if (userDBAdaptor.count(queryExists).getNumMatches() == 0) {
                throw new CatalogException("There is no filter called " + name + " for user " + userId);
            }

            OpenCGAResult result = userDBAdaptor.updateFilter(userId, name, params);
            User.Filter filter = getFilter(userId, name);
            if (filter == null) {
                throw new CatalogException("Internal error: The filter " + name + " could not be found.");
            }
            auditManager.auditUser(userIdAux, Enums.Action.CHANGE_USER_CONFIG, userId, auditParams,
                    new AuditRecord.Status(AuditRecord.Status.Result.SUCCESS));
            return new OpenCGAResult<>(result.getTime(), Collections.emptyList(), 1, Collections.singletonList(filter), 1);
        } catch (CatalogException e) {
            auditManager.auditUser(userIdAux, Enums.Action.CHANGE_USER_CONFIG, userId, auditParams,
                    new AuditRecord.Status(AuditRecord.Status.Result.ERROR, e.getError()));
            throw e;
        }
    }

    /**
     * Delete the filter.
     * <p>
     * @param userId    user id to whom the filter should be deleted.
     * @param name      filter name to be deleted.
     * @param token session id of the user asking to delete the filter.
     * @return the deleted filter.
     * @throws CatalogException when the filter cannot be removed or the name is not correct or if the user corresponding to the
     *                          session id is not the same as the provided user id.
     */
    public OpenCGAResult<User.Filter> deleteFilter(String userId, String name, String token) throws CatalogException {
        ParamUtils.checkParameter(userId, "userId");
        ParamUtils.checkParameter(token, "token");
        ParamUtils.checkParameter(name, "name");

        String userIdAux = getUserId(token);
        ObjectMap auditParams = new ObjectMap()
                .append("userId", userId)
                .append("name", name)
                .append("token", token);
        try {
            userDBAdaptor.checkId(userId);
            if (!userId.equals(userIdAux)) {
                throw new CatalogException("User " + userIdAux + " is not authorised to delete filters for user " + userId);
            }

            User.Filter filter = getFilter(userId, name);
            if (filter == null) {
                throw new CatalogException("There is no filter called " + name + " for user " + userId);
            }

            OpenCGAResult result = userDBAdaptor.deleteFilter(userId, name);
            auditManager.auditUser(userIdAux, Enums.Action.CHANGE_USER_CONFIG, userId, auditParams,
                    new AuditRecord.Status(AuditRecord.Status.Result.SUCCESS));
            return new OpenCGAResult<>(result.getTime(), Collections.emptyList(), 1, Collections.singletonList(filter), 1);
        } catch (CatalogException e) {
            auditManager.auditUser(userIdAux, Enums.Action.CHANGE_USER_CONFIG, userId, auditParams,
                    new AuditRecord.Status(AuditRecord.Status.Result.ERROR, e.getError()));
            throw e;
        }
    }

    /**
     * Retrieves a filter.
     * <p>
     * @param userId    user id having the filter stored.
     * @param name      Filter name to be fetched.
     * @param token session id of the user fetching the filter.
     * @return the filter.
     * @throws CatalogException if the user corresponding to the session id is not the same as the provided user id.
     */
    public OpenCGAResult<User.Filter> getFilter(String userId, String name, String token) throws CatalogException {
        ParamUtils.checkParameter(userId, "userId");
        ParamUtils.checkParameter(token, "sessionId");
        ParamUtils.checkParameter(name, "name");

        String userIdAux = getUserId(token);
        ObjectMap auditParams = new ObjectMap()
                .append("userId", userId)
                .append("name", name)
                .append("token", token);
        try {
            userDBAdaptor.checkId(userId);
            if (!userId.equals(userIdAux)) {
                throw new CatalogException("User " + userIdAux + " is not authorised to get filters from user " + userId);
            }

            User.Filter filter = getFilter(userId, name);
            auditManager.auditUser(userIdAux, Enums.Action.FETCH_USER_CONFIG, userId, auditParams,
                    new AuditRecord.Status(AuditRecord.Status.Result.SUCCESS));

            if (filter == null) {
                throw new CatalogException("Filter " + name + " not found.");
            } else {
                return new OpenCGAResult<>(0, Collections.emptyList(), 1, Collections.singletonList(filter), 1);
            }
        } catch (CatalogException e) {
            auditManager.auditUser(userIdAux, Enums.Action.FETCH_USER_CONFIG, userId, auditParams,
                    new AuditRecord.Status(AuditRecord.Status.Result.ERROR, e.getError()));
            throw e;
        }
    }

    /**
     * Retrieves all the user filters.
     *
     * @param userId    user id having the filters.
     * @param token session id of the user fetching the filters.
     * @return the filters.
     * @throws CatalogException if the user corresponding to the session id is not the same as the provided user id.
     */
    public OpenCGAResult<User.Filter> getAllFilters(String userId, String token) throws CatalogException {
        ParamUtils.checkParameter(userId, "userId");
        ParamUtils.checkParameter(token, "sessionId");

        String userIdAux = getUserId(token);
        ObjectMap auditParams = new ObjectMap()
                .append("userId", userId)
                .append("token", token);
        try {
            userDBAdaptor.checkId(userId);
            if (!userId.equals(userIdAux)) {
                throw new CatalogException("User " + userIdAux + " is not authorised to get filters from user " + userId);
            }

            Query query = new Query()
                    .append(UserDBAdaptor.QueryParams.ID.key(), userId);
            QueryOptions queryOptions = new QueryOptions(QueryOptions.INCLUDE, UserDBAdaptor.QueryParams.CONFIGS.key());
            OpenCGAResult<User> userDataResult = userDBAdaptor.get(query, queryOptions);

            if (userDataResult.getNumResults() != 1) {
                throw new CatalogException("Internal error: User " + userId + " not found.");
            }

            List<User.Filter> filters = userDataResult.first().getConfigs().getFilters();
            auditManager.auditUser(userIdAux, Enums.Action.FETCH_USER_CONFIG, userId, auditParams,
                    new AuditRecord.Status(AuditRecord.Status.Result.SUCCESS));

            return new OpenCGAResult<>(0, Collections.emptyList(), filters.size(), filters, filters.size());
        } catch (CatalogException e) {
            auditManager.auditUser(userIdAux, Enums.Action.FETCH_USER_CONFIG, userId, auditParams,
                    new AuditRecord.Status(AuditRecord.Status.Result.ERROR, e.getError()));
            throw e;
        }
    }

    /**
     * Creates or updates a configuration.
     * <p>
     * @param userId    user id to whom the config will be associated.
     * @param name      Name of the configuration (normally, name of the application).
     * @param config    Configuration to be stored.
     * @param token session id of the user asking to store the config.
     * @return the set configuration.
     * @throws CatalogException if the user corresponding to the session id is not the same as the provided user id.
     */
    public OpenCGAResult setConfig(String userId, String name, Map<String, Object> config, String token) throws CatalogException {
        ParamUtils.checkParameter(userId, "userId");
        ParamUtils.checkParameter(token, "sessionId");
        ParamUtils.checkParameter(name, "name");
        ParamUtils.checkObj(config, "ObjectMap");

        String userIdAux = getUserId(token);
        ObjectMap auditParams = new ObjectMap()
                .append("userId", userId)
                .append("name", name)
                .append("config", config)
                .append("token", token);

        try {
            userDBAdaptor.checkId(userId);
            if (!userId.equals(userIdAux)) {
                throw new CatalogException("User " + userIdAux + " is not authorised to set configuration for user " + userId);
            }

            OpenCGAResult result = userDBAdaptor.setConfig(userId, name, config);
            auditManager.auditUser(userIdAux, Enums.Action.CHANGE_USER_CONFIG, userId, auditParams,
                    new AuditRecord.Status(AuditRecord.Status.Result.SUCCESS));

            return new OpenCGAResult(result.getTime(), Collections.emptyList(), 1, Collections.singletonList(config), 1);
        } catch (CatalogException e) {
            auditManager.auditUser(userIdAux, Enums.Action.CHANGE_USER_CONFIG, userId, auditParams,
                    new AuditRecord.Status(AuditRecord.Status.Result.ERROR, e.getError()));
            throw e;
        }
    }

    /**
     * Deletes a configuration.
     * <p>
     * @param userId    user id to whom the configuration should be deleted.
     * @param name      Name of the configuration to be deleted (normally, name of the application).
     * @param token session id of the user asking to delete the configuration.
     * @return the deleted configuration.
     * @throws CatalogException if the user corresponding to the session id is not the same as the provided user id or the configuration
     *                          did not exist.
     */
    public OpenCGAResult deleteConfig(String userId, String name, String token) throws CatalogException {
        ParamUtils.checkParameter(userId, "userId");
        ParamUtils.checkParameter(token, "token");
        ParamUtils.checkParameter(name, "name");

        String userIdAux = getUserId(token);

        ObjectMap auditParams = new ObjectMap()
                .append("userId", userId)
                .append("name", name)
                .append("token", token);
        try {
            userDBAdaptor.checkId(userId);
            if (!userId.equals(userIdAux)) {
                throw new CatalogException("User " + userIdAux + " is not authorised to delete the configuration of user " + userId);
            }

            QueryOptions options = new QueryOptions(QueryOptions.INCLUDE, UserDBAdaptor.QueryParams.CONFIGS.key());
            OpenCGAResult<User> userDataResult = userDBAdaptor.get(userId, options, "");
            if (userDataResult.getNumResults() == 0) {
                throw new CatalogException("Internal error: Could not get user " + userId);
            }

            User.UserConfiguration configs = userDataResult.first().getConfigs();
            if (configs == null) {
                throw new CatalogException("Internal error: Configuration object is null.");
            }

            if (configs.get(name) == null) {
                throw new CatalogException("Error: Cannot delete configuration with name " + name + ". Configuration name not found.");
            }

            OpenCGAResult result = userDBAdaptor.deleteConfig(userId, name);
            auditManager.auditUser(userIdAux, Enums.Action.CHANGE_USER_CONFIG, userId, auditParams,
                    new AuditRecord.Status(AuditRecord.Status.Result.SUCCESS));
            return new OpenCGAResult(result.getTime(), Collections.emptyList(), 1, Collections.singletonList(configs.get(name)), 1);
        } catch (CatalogException e) {
            auditManager.auditUser(userIdAux, Enums.Action.CHANGE_USER_CONFIG, userId, auditParams,
                    new AuditRecord.Status(AuditRecord.Status.Result.ERROR, e.getError()));
            throw e;
        }
    }

    /**
     * Retrieves a configuration.
     * <p>
     * @param userId    user id having the configuration stored.
     * @param name      Name of the configuration to be fetched (normally, name of the application).
     * @param sessionId session id of the user attempting to fetch the configuration.
     * @return the configuration.
     * @throws CatalogException if the user corresponding to the session id is not the same as the provided user id or the configuration
     *                          does not exist.
     */
    public OpenCGAResult getConfig(String userId, String name, String sessionId) throws CatalogException {
        ParamUtils.checkParameter(userId, "userId");
        ParamUtils.checkParameter(sessionId, "sessionId");

        String userIdAux = getUserId(sessionId);
        ObjectMap auditParams = new ObjectMap()
                .append("userId", userId)
                .append("name", name)
                .append("sessionId", sessionId);
        try {
            userDBAdaptor.checkId(userId);
            if (!userId.equals(userIdAux)) {
                throw new CatalogException("User " + userIdAux + " is not authorised to fetch the configuration of user " + userId);
            }

            QueryOptions options = new QueryOptions(QueryOptions.INCLUDE, UserDBAdaptor.QueryParams.CONFIGS.key());
            OpenCGAResult<User> userDataResult = userDBAdaptor.get(userId, options, "");
            if (userDataResult.getNumResults() == 0) {
                throw new CatalogException("Internal error: Could not get user " + userId);
            }

            User.UserConfiguration configs = userDataResult.first().getConfigs();
            if (configs == null) {
                throw new CatalogException("Internal error: Configuration object is null.");
            }

            if (StringUtils.isNotEmpty(name) && configs.get(name) == null) {
                throw new CatalogException("Error: Cannot fetch configuration with name " + name + ". Configuration name not found.");
            }

            // Remove filters form configs array
            configs.remove("filters");
            Map configMap = StringUtils.isEmpty(name) ? configs : (Map) configs.get(name);
            auditManager.auditUser(userIdAux, Enums.Action.FETCH_USER_CONFIG, userId, auditParams,
                    new AuditRecord.Status(AuditRecord.Status.Result.SUCCESS));

            return new OpenCGAResult(userDataResult.getTime(), userDataResult.getEvents(), 1, Collections.singletonList(configMap), 1);
        } catch (CatalogException e) {
            auditManager.auditUser(userIdAux, Enums.Action.FETCH_USER_CONFIG, userId, auditParams,
                    new AuditRecord.Status(AuditRecord.Status.Result.ERROR, e.getError()));
            throw e;
        }
    }


    private User.Filter getFilter(String userId, String name) throws CatalogException {
        Query query = new Query()
                .append(UserDBAdaptor.QueryParams.ID.key(), userId);
        QueryOptions queryOptions = new QueryOptions(QueryOptions.INCLUDE, UserDBAdaptor.QueryParams.CONFIGS.key());
        OpenCGAResult<User> userDataResult = userDBAdaptor.get(query, queryOptions);

        if (userDataResult.getNumResults() != 1) {
            throw new CatalogException("Internal error: User " + userId + " not found.");
        }

        for (User.Filter filter : userDataResult.first().getConfigs().getFilters()) {
            if (name.equals(filter.getName())) {
                return filter;
            }
        }

        return null;
    }

    private void validateUserAndToken(String userId, String jwtToken) throws CatalogException {
        boolean validToken;
        for (AuthenticationManager authenticationManager : authenticationManagerMap.values()) {
            try {
                if (!userId.equals(authenticationManager.getUserId(jwtToken))) {
                    validToken = false;
                } else {
                    validToken = true;
                }
            } catch (CatalogException e) {
                // The authentication manager might have failed because that token was generated with a different auth manager
                continue;
            }

            if (validToken) {
                return;
            } else {
                throw new CatalogException("Invalid authentication token for user: " + userId);
            }
        }

        throw new CatalogException("Invalid authentication token for user: " + userId);
    }

    private void checkUserExists(String userId) throws CatalogException {
        if (userId.toLowerCase().equals(ANONYMOUS)) {
            throw new CatalogException("Permission denied: Cannot create users with special treatments in catalog.");
        }

        if (userDBAdaptor.exists(userId)) {
            throw new CatalogException("The user already exists in our database.");
        }
    }

    private String getAuthenticationOriginId(String userId) throws CatalogException {
        OpenCGAResult<User> user = userDBAdaptor.get(userId, new QueryOptions(), "");
        if (user == null || user.getNumResults() == 0) {
            throw new CatalogException(userId + " user not found");
        }
        return user.first().getAccount().getAuthentication().getId();
    }

}
