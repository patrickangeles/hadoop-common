/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.security.authorize;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.classification.InterfaceStability;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.security.UserGroupInformation;

/**
 * Class representing a configured access control list.
 */
@InterfaceAudience.LimitedPrivate({"HDFS", "MapReduce"})
@InterfaceStability.Evolving
public class AccessControlList implements Writable {
  
  // Indicates an ACL string that represents access to all users
  public static final String WILDCARD_ACL_VALUE = "*";
  private static final int INITIAL_CAPACITY = 256;

  // Set of users who are granted access.
  private Set<String> users;
  // Set of groups which are granted access
  private Set<String> groups;
  // Whether all users are granted access.
  private boolean allAllowed;
  
  /**
   * Construct a new ACL from a String representation of the same.
   * 
   * The String is a a comma separated list of users and groups.
   * The user list comes first and is separated by a space followed 
   * by the group list. For e.g. "user1,user2 group1,group2"
   * 
   * @param aclString String representation of the ACL
   */
  public AccessControlList(String aclString) {
    buildACL(aclString);
  }

  // build ACL from the given string
  private void buildACL(String aclString) {
    users = new TreeSet<String>();
    groups = new TreeSet<String>();
    if (isWildCardACLValue(aclString)) {
      allAllowed = true;
    } else {
      allAllowed = false;
      String[] userGroupStrings = aclString.split(" ", 2);
      
      if (userGroupStrings.length >= 1) {
        String[] usersStr = userGroupStrings[0].split(",");
        if (usersStr.length >= 1) {
          addToSet(users, usersStr);
        }
      }
      
      if (userGroupStrings.length == 2) {
        String[] groupsStr = userGroupStrings[1].split(",");
        if (groupsStr.length >= 1) {
          addToSet(groups, groupsStr);
        }
      }
    }
  }
  
  private boolean isWildCardACLValue(String aclString) {
    if (aclString.contains(WILDCARD_ACL_VALUE) && 
        aclString.trim().equals(WILDCARD_ACL_VALUE)) {
      return true;
    }
    return false;
  }

  public boolean isAllAllowed() {
    return allAllowed;
  }
  
  /**
   * Add user to the names of users allowed for this service.
   * 
   * @param user
   *          The user name
   */
  public void addUser(String user) {
    if (isWildCardACLValue(user)) {
      throw new IllegalArgumentException("User " + user + " can not be added");
    }
    if (!isAllAllowed()) {
      users.add(user);
    }
  }

  /**
   * Add group to the names of groups allowed for this service.
   * 
   * @param group
   *          The group name
   */
  public void addGroup(String group) {
    if (isWildCardACLValue(group)) {
      throw new IllegalArgumentException("Group " + group + " can not be added");
    }
    if (!isAllAllowed()) {
      groups.add(group);
    }
  }

  /**
   * Remove user from the names of users allowed for this service.
   * 
   * @param user
   *          The user name
   */
  public void removeUser(String user) {
    if (isWildCardACLValue(user)) {
      throw new IllegalArgumentException("User " + user + " can not be removed");
    }
    if (!isAllAllowed()) {
      users.remove(user);
    }
  }

  /**
   * Remove group from the names of groups allowed for this service.
   * 
   * @param group
   *          The group name
   */
  public void removeGroup(String group) {
    if (isWildCardACLValue(group)) {
      throw new IllegalArgumentException("Group " + group
          + " can not be removed");
    }
    if (!isAllAllowed()) {
      groups.remove(group);
    }
  }

  /**
   * Get the names of users allowed for this service.
   * @return the set of user names. the set must not be modified.
   */
  Set<String> getUsers() {
    return users;
  }
  
  /**
   * Get the names of user groups allowed for this service.
   * @return the set of group names. the set must not be modified.
   */
  Set<String> getGroups() {
    return groups;
  }

  public boolean isUserAllowed(UserGroupInformation ugi) {
    if (allAllowed || users.contains(ugi.getShortUserName())) {
      return true;
    } else {
      for(String group: ugi.getGroupNames()) {
        if (groups.contains(group)) {
          return true;
        }
      }
    }
    return false;
  }
  
  private static final void addToSet(Set<String> set, String[] strings) {
    for (String s : strings) {
      s = s.trim();
      if (s.length() > 0) {
        set.add(s);
      }
    }
  }

  /**
   * Returns descriptive way of users and groups that are part of this ACL.
   * Use {@link #getAclString()} to get the exact String that can be given to
   * the constructor of AccessControlList to create a new instance.
   */
  @Override
  public String toString() {
    String str = null;

    if (allAllowed) {
      str = "All users are allowed";
    }
    else if (users.isEmpty() && groups.isEmpty()) {
      str = "No users are allowed";
    }
    else {
      String usersStr = null;
      String groupsStr = null;
      if (!users.isEmpty()) {
        usersStr = users.toString();
      }
      if (!groups.isEmpty()) {
        groupsStr = groups.toString();
      }

      if (!users.isEmpty() && !groups.isEmpty()) {
        str = "Users " + usersStr + " and members of the groups "
            + groupsStr + " are allowed";
      }
      else if (!users.isEmpty()) {
        str = "Users " + usersStr + " are allowed";
      }
      else {// users is empty array and groups is nonempty
        str = "Members of the groups "
            + groupsStr + " are allowed";
      }
    }

    return str;
  }

  /**
   * Returns the access control list as a String that can be used for building a
   * new instance by sending it to the constructor of {@link AccessControlList}.
   */
  public String getAclString() {
    StringBuilder sb = new StringBuilder(INITIAL_CAPACITY);
    if (allAllowed) {
      sb.append('*');
    }
    else {
      sb.append(getUsersString());
      sb.append(" ");
      sb.append(getGroupsString());
    }
    return sb.toString();
  }

  /**
   * Serializes the AccessControlList object
   */
  public void write(DataOutput out) throws IOException {
    String aclString = getAclString();
    Text.writeString(out, aclString);
  }

  /**
   * Deserializes the AccessControlList object
   */
  public void readFields(DataInput in) throws IOException {
    String aclString = Text.readString(in);
    buildACL(aclString);
  }

  // Returns comma-separated concatenated single String of the set 'users'
  private String getUsersString() {
    return getString(users);
  }

  // Returns comma-separated concatenated single String of the set 'groups'
  private String getGroupsString() {
    return getString(groups);
  }

  // Returns comma-separated concatenated single String of all strings of
  // the given set
  private String getString(Set<String> strings) {
    StringBuilder sb = new StringBuilder(INITIAL_CAPACITY);
    boolean first = true;
    for(String str: strings) {
      if (!first) {
        sb.append(",");
      } else {
        first = false;
      }
      sb.append(str);
    }
    return sb.toString();
  }
}