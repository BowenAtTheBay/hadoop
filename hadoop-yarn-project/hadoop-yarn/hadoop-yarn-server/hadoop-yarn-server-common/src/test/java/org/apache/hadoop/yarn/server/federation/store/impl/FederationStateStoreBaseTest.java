/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership.  The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.apache.hadoop.yarn.server.federation.store.impl;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.hadoop.yarn.server.federation.store.FederationStateStore;
import org.apache.hadoop.yarn.server.federation.store.records.AddApplicationHomeSubClusterRequest;
import org.apache.hadoop.yarn.server.federation.store.records.AddApplicationHomeSubClusterResponse;
import org.apache.hadoop.yarn.server.federation.store.records.ApplicationHomeSubCluster;
import org.apache.hadoop.yarn.server.federation.store.records.DeleteApplicationHomeSubClusterRequest;
import org.apache.hadoop.yarn.server.federation.store.records.DeleteApplicationHomeSubClusterResponse;
import org.apache.hadoop.yarn.server.federation.store.records.GetApplicationHomeSubClusterRequest;
import org.apache.hadoop.yarn.server.federation.store.records.GetApplicationHomeSubClusterResponse;
import org.apache.hadoop.yarn.server.federation.store.records.GetApplicationsHomeSubClusterRequest;
import org.apache.hadoop.yarn.server.federation.store.records.GetApplicationsHomeSubClusterResponse;
import org.apache.hadoop.yarn.server.federation.store.records.GetSubClusterInfoRequest;
import org.apache.hadoop.yarn.server.federation.store.records.GetSubClusterPoliciesConfigurationsRequest;
import org.apache.hadoop.yarn.server.federation.store.records.GetSubClusterPoliciesConfigurationsResponse;
import org.apache.hadoop.yarn.server.federation.store.records.GetSubClusterPolicyConfigurationRequest;
import org.apache.hadoop.yarn.server.federation.store.records.GetSubClusterPolicyConfigurationResponse;
import org.apache.hadoop.yarn.server.federation.store.records.GetSubClustersInfoRequest;
import org.apache.hadoop.yarn.server.federation.store.records.SetSubClusterPolicyConfigurationRequest;
import org.apache.hadoop.yarn.server.federation.store.records.SetSubClusterPolicyConfigurationResponse;
import org.apache.hadoop.yarn.server.federation.store.records.SubClusterDeregisterRequest;
import org.apache.hadoop.yarn.server.federation.store.records.SubClusterHeartbeatRequest;
import org.apache.hadoop.yarn.server.federation.store.records.SubClusterId;
import org.apache.hadoop.yarn.server.federation.store.records.SubClusterInfo;
import org.apache.hadoop.yarn.server.federation.store.records.SubClusterPolicyConfiguration;
import org.apache.hadoop.yarn.server.federation.store.records.SubClusterRegisterRequest;
import org.apache.hadoop.yarn.server.federation.store.records.SubClusterRegisterResponse;
import org.apache.hadoop.yarn.server.federation.store.records.SubClusterState;
import org.apache.hadoop.yarn.server.federation.store.records.UpdateApplicationHomeSubClusterRequest;
import org.apache.hadoop.yarn.server.federation.store.records.UpdateApplicationHomeSubClusterResponse;
import org.apache.hadoop.yarn.util.MonotonicClock;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Base class for FederationMembershipStateStore implementations.
 */
public abstract class FederationStateStoreBaseTest {

  private static final MonotonicClock CLOCK = new MonotonicClock();
  private FederationStateStore stateStore = createStateStore();

  protected abstract FederationStateStore createStateStore();

  @Before
  public void before() throws IOException, YarnException {
    stateStore.init(new Configuration());
  }

  @After
  public void after() throws Exception {
    stateStore.close();
  }

  // Test FederationMembershipStateStore

  @Test
  public void testRegisterSubCluster() throws Exception {
    SubClusterId subClusterId = SubClusterId.newInstance("SC");
    SubClusterInfo subClusterInfo = createSubClusterInfo(subClusterId);

    SubClusterRegisterResponse result = stateStore.registerSubCluster(
        SubClusterRegisterRequest.newInstance(subClusterInfo));

    Assert.assertNotNull(result);
    Assert.assertEquals(subClusterInfo, querySubClusterInfo(subClusterId));
  }

  @Test
  public void testDeregisterSubCluster() throws Exception {
    SubClusterId subClusterId = SubClusterId.newInstance("SC");
    registerSubCluster(subClusterId);

    SubClusterDeregisterRequest deregisterRequest = SubClusterDeregisterRequest
        .newInstance(subClusterId, SubClusterState.SC_UNREGISTERED);

    stateStore.deregisterSubCluster(deregisterRequest);

    Assert.assertEquals(SubClusterState.SC_UNREGISTERED,
        querySubClusterInfo(subClusterId).getState());
  }

  @Test
  public void testDeregisterSubClusterUnknownSubCluster() throws Exception {
    SubClusterId subClusterId = SubClusterId.newInstance("SC");

    SubClusterDeregisterRequest deregisterRequest = SubClusterDeregisterRequest
        .newInstance(subClusterId, SubClusterState.SC_UNREGISTERED);
    try {
      stateStore.deregisterSubCluster(deregisterRequest);
      Assert.fail();
    } catch (YarnException e) {
      Assert.assertTrue(e.getMessage().startsWith("SubCluster SC not found"));
    }
  }

  @Test
  public void testGetSubClusterInfo() throws Exception {

    SubClusterId subClusterId = SubClusterId.newInstance("SC");
    SubClusterInfo subClusterInfo = createSubClusterInfo(subClusterId);
    registerSubCluster(subClusterId);

    GetSubClusterInfoRequest request =
        GetSubClusterInfoRequest.newInstance(subClusterId);
    Assert.assertEquals(subClusterInfo,
        stateStore.getSubCluster(request).getSubClusterInfo());
  }

  @Test
  public void testGetSubClusterInfoUnknownSubCluster() throws Exception {
    SubClusterId subClusterId = SubClusterId.newInstance("SC");
    GetSubClusterInfoRequest request =
        GetSubClusterInfoRequest.newInstance(subClusterId);

    try {
      stateStore.getSubCluster(request).getSubClusterInfo();
      Assert.fail();
    } catch (YarnException e) {
      Assert.assertTrue(
          e.getMessage().startsWith("Subcluster SC does not exist"));
    }
  }

  @Test
  public void testGetAllSubClustersInfo() throws Exception {

    SubClusterId subClusterId1 = SubClusterId.newInstance("SC1");
    SubClusterInfo subClusterInfo1 = createSubClusterInfo(subClusterId1);

    SubClusterId subClusterId2 = SubClusterId.newInstance("SC2");
    SubClusterInfo subClusterInfo2 = createSubClusterInfo(subClusterId2);

    stateStore.registerSubCluster(
        SubClusterRegisterRequest.newInstance(subClusterInfo1));
    stateStore.registerSubCluster(
        SubClusterRegisterRequest.newInstance(subClusterInfo2));

    stateStore.subClusterHeartbeat(SubClusterHeartbeatRequest
        .newInstance(subClusterId1, SubClusterState.SC_RUNNING, ""));
    stateStore.subClusterHeartbeat(SubClusterHeartbeatRequest
        .newInstance(subClusterId2, SubClusterState.SC_UNHEALTHY, ""));

    Assert.assertTrue(
        stateStore.getSubClusters(GetSubClustersInfoRequest.newInstance(true))
            .getSubClusters().contains(subClusterInfo1));
    Assert.assertFalse(
        stateStore.getSubClusters(GetSubClustersInfoRequest.newInstance(true))
            .getSubClusters().contains(subClusterInfo2));

    Assert.assertTrue(
        stateStore.getSubClusters(GetSubClustersInfoRequest.newInstance(false))
            .getSubClusters().contains(subClusterInfo1));
    Assert.assertTrue(
        stateStore.getSubClusters(GetSubClustersInfoRequest.newInstance(false))
            .getSubClusters().contains(subClusterInfo2));
  }

  @Test
  public void testSubClusterHeartbeat() throws Exception {
    SubClusterId subClusterId = SubClusterId.newInstance("SC");
    registerSubCluster(subClusterId);

    SubClusterHeartbeatRequest heartbeatRequest = SubClusterHeartbeatRequest
        .newInstance(subClusterId, SubClusterState.SC_RUNNING, "cabability");
    stateStore.subClusterHeartbeat(heartbeatRequest);

    Assert.assertEquals(SubClusterState.SC_RUNNING,
        querySubClusterInfo(subClusterId).getState());
    Assert.assertNotNull(querySubClusterInfo(subClusterId).getLastHeartBeat());
  }

  @Test
  public void testSubClusterHeartbeatUnknownSubCluster() throws Exception {
    SubClusterId subClusterId = SubClusterId.newInstance("SC");
    SubClusterHeartbeatRequest heartbeatRequest = SubClusterHeartbeatRequest
        .newInstance(subClusterId, SubClusterState.SC_RUNNING, "cabability");

    try {
      stateStore.subClusterHeartbeat(heartbeatRequest);
      Assert.fail();
    } catch (YarnException e) {
      Assert.assertTrue(e.getMessage()
          .startsWith("Subcluster SC does not exist; cannot heartbeat"));
    }
  }

  // Test FederationApplicationHomeSubClusterStore

  @Test
  public void testAddApplicationHomeSubClusterMap() throws Exception {
    ApplicationId appId = ApplicationId.newInstance(1, 1);
    SubClusterId subClusterId = SubClusterId.newInstance("SC");
    ApplicationHomeSubCluster ahsc =
        ApplicationHomeSubCluster.newInstance(appId, subClusterId);

    AddApplicationHomeSubClusterRequest request =
        AddApplicationHomeSubClusterRequest.newInstance(ahsc);
    AddApplicationHomeSubClusterResponse response =
        stateStore.addApplicationHomeSubClusterMap(request);

    Assert.assertNotNull(response);
    Assert.assertEquals(subClusterId, queryApplicationHomeSC(appId));

  }

  @Test
  public void testAddApplicationHomeSubClusterMapAppAlreadyExists()
      throws Exception {
    ApplicationId appId = ApplicationId.newInstance(1, 1);
    SubClusterId subClusterId1 = SubClusterId.newInstance("SC1");
    addApplicationHomeSC(appId, subClusterId1);

    SubClusterId subClusterId2 = SubClusterId.newInstance("SC2");
    ApplicationHomeSubCluster ahsc2 =
        ApplicationHomeSubCluster.newInstance(appId, subClusterId2);

    try {
      stateStore.addApplicationHomeSubClusterMap(
          AddApplicationHomeSubClusterRequest.newInstance(ahsc2));
      Assert.fail();
    } catch (YarnException e) {
      Assert.assertTrue(e.getMessage()
          .startsWith("Application " + appId.toString() + " already exists"));
    }

    Assert.assertEquals(subClusterId1, queryApplicationHomeSC(appId));

  }

  @Test
  public void testDeleteApplicationHomeSubClusterMap() throws Exception {
    ApplicationId appId = ApplicationId.newInstance(1, 1);
    SubClusterId subClusterId = SubClusterId.newInstance("SC");
    addApplicationHomeSC(appId, subClusterId);

    DeleteApplicationHomeSubClusterRequest delRequest =
        DeleteApplicationHomeSubClusterRequest.newInstance(appId);

    DeleteApplicationHomeSubClusterResponse response =
        stateStore.deleteApplicationHomeSubClusterMap(delRequest);

    Assert.assertNotNull(response);
    try {
      queryApplicationHomeSC(appId);
      Assert.fail();
    } catch (YarnException e) {
      Assert.assertTrue(e.getMessage()
          .startsWith("Application " + appId + " does not exist"));
    }

  }

  @Test
  public void testDeleteApplicationHomeSubClusterMapUnknownApp()
      throws Exception {
    ApplicationId appId = ApplicationId.newInstance(1, 1);
    DeleteApplicationHomeSubClusterRequest delRequest =
        DeleteApplicationHomeSubClusterRequest.newInstance(appId);

    try {
      stateStore.deleteApplicationHomeSubClusterMap(delRequest);
      Assert.fail();
    } catch (YarnException e) {
      Assert.assertTrue(e.getMessage()
          .startsWith("Application " + appId.toString() + " does not exist"));
    }
  }

  @Test
  public void testGetApplicationHomeSubClusterMap() throws Exception {
    ApplicationId appId = ApplicationId.newInstance(1, 1);
    SubClusterId subClusterId = SubClusterId.newInstance("SC");
    addApplicationHomeSC(appId, subClusterId);

    GetApplicationHomeSubClusterRequest getRequest =
        GetApplicationHomeSubClusterRequest.newInstance(appId);

    GetApplicationHomeSubClusterResponse result =
        stateStore.getApplicationHomeSubClusterMap(getRequest);

    Assert.assertEquals(appId,
        result.getApplicationHomeSubCluster().getApplicationId());
    Assert.assertEquals(subClusterId,
        result.getApplicationHomeSubCluster().getHomeSubCluster());
  }

  @Test
  public void testGetApplicationHomeSubClusterMapUnknownApp() throws Exception {
    ApplicationId appId = ApplicationId.newInstance(1, 1);
    GetApplicationHomeSubClusterRequest request =
        GetApplicationHomeSubClusterRequest.newInstance(appId);

    try {
      stateStore.getApplicationHomeSubClusterMap(request);
      Assert.fail();
    } catch (YarnException e) {
      Assert.assertTrue(e.getMessage()
          .startsWith("Application " + appId.toString() + " does not exist"));
    }
  }

  @Test
  public void testGetApplicationsHomeSubClusterMap() throws Exception {
    ApplicationId appId1 = ApplicationId.newInstance(1, 1);
    SubClusterId subClusterId1 = SubClusterId.newInstance("SC1");
    ApplicationHomeSubCluster ahsc1 =
        ApplicationHomeSubCluster.newInstance(appId1, subClusterId1);

    ApplicationId appId2 = ApplicationId.newInstance(1, 2);
    SubClusterId subClusterId2 = SubClusterId.newInstance("SC2");
    ApplicationHomeSubCluster ahsc2 =
        ApplicationHomeSubCluster.newInstance(appId2, subClusterId2);

    addApplicationHomeSC(appId1, subClusterId1);
    addApplicationHomeSC(appId2, subClusterId2);

    GetApplicationsHomeSubClusterRequest getRequest =
        GetApplicationsHomeSubClusterRequest.newInstance();

    GetApplicationsHomeSubClusterResponse result =
        stateStore.getApplicationsHomeSubClusterMap(getRequest);

    Assert.assertEquals(2, result.getAppsHomeSubClusters().size());
    Assert.assertTrue(result.getAppsHomeSubClusters().contains(ahsc1));
    Assert.assertTrue(result.getAppsHomeSubClusters().contains(ahsc2));
  }

  @Test
  public void testUpdateApplicationHomeSubClusterMap() throws Exception {
    ApplicationId appId = ApplicationId.newInstance(1, 1);
    SubClusterId subClusterId1 = SubClusterId.newInstance("SC1");
    addApplicationHomeSC(appId, subClusterId1);

    SubClusterId subClusterId2 = SubClusterId.newInstance("SC2");
    ApplicationHomeSubCluster ahscUpdate =
        ApplicationHomeSubCluster.newInstance(appId, subClusterId2);

    UpdateApplicationHomeSubClusterRequest updateRequest =
        UpdateApplicationHomeSubClusterRequest.newInstance(ahscUpdate);

    UpdateApplicationHomeSubClusterResponse response =
        stateStore.updateApplicationHomeSubClusterMap(updateRequest);

    Assert.assertNotNull(response);

    Assert.assertEquals(subClusterId2, queryApplicationHomeSC(appId));
  }

  @Test
  public void testUpdateApplicationHomeSubClusterMapUnknownApp()
      throws Exception {
    ApplicationId appId = ApplicationId.newInstance(1, 1);
    SubClusterId subClusterId1 = SubClusterId.newInstance("SC1");
    ApplicationHomeSubCluster ahsc =
        ApplicationHomeSubCluster.newInstance(appId, subClusterId1);

    UpdateApplicationHomeSubClusterRequest updateRequest =
        UpdateApplicationHomeSubClusterRequest.newInstance(ahsc);

    try {
      stateStore.updateApplicationHomeSubClusterMap((updateRequest));
      Assert.fail();
    } catch (YarnException e) {
      Assert.assertTrue(e.getMessage()
          .startsWith("Application " + appId.toString() + " does not exist"));
    }
  }

  // Test FederationPolicyStore

  @Test
  public void testSetPolicyConfiguration() throws Exception {
    SetSubClusterPolicyConfigurationRequest request =
        SetSubClusterPolicyConfigurationRequest
            .newInstance(createSCPolicyConf("Queue", "PolicyType"));

    SetSubClusterPolicyConfigurationResponse result =
        stateStore.setPolicyConfiguration(request);

    Assert.assertNotNull(result);
    Assert.assertEquals(createSCPolicyConf("Queue", "PolicyType"),
        queryPolicy("Queue"));

  }

  @Test
  public void testSetPolicyConfigurationUpdateExisting() throws Exception {
    setPolicyConf("Queue", "PolicyType1");

    SetSubClusterPolicyConfigurationRequest request2 =
        SetSubClusterPolicyConfigurationRequest
            .newInstance(createSCPolicyConf("Queue", "PolicyType2"));
    SetSubClusterPolicyConfigurationResponse result =
        stateStore.setPolicyConfiguration(request2);

    Assert.assertNotNull(result);
    Assert.assertEquals(createSCPolicyConf("Queue", "PolicyType2"),
        queryPolicy("Queue"));
  }

  @Test
  public void testGetPolicyConfiguration() throws Exception {
    setPolicyConf("Queue", "PolicyType");

    GetSubClusterPolicyConfigurationRequest getRequest =
        GetSubClusterPolicyConfigurationRequest.newInstance("Queue");
    GetSubClusterPolicyConfigurationResponse result =
        stateStore.getPolicyConfiguration(getRequest);

    Assert.assertNotNull(result);
    Assert.assertEquals(createSCPolicyConf("Queue", "PolicyType"),
        result.getPolicyConfiguration());

  }

  @Test
  public void testGetPolicyConfigurationUnknownQueue() throws Exception {

    GetSubClusterPolicyConfigurationRequest request =
        GetSubClusterPolicyConfigurationRequest.newInstance("Queue");
    try {
      stateStore.getPolicyConfiguration(request);
      Assert.fail();
    } catch (YarnException e) {
      Assert.assertTrue(
          e.getMessage().startsWith("Policy for queue Queue does not exist"));
    }
  }

  @Test
  public void testGetPoliciesConfigurations() throws Exception {
    setPolicyConf("Queue1", "PolicyType1");
    setPolicyConf("Queue2", "PolicyType2");

    GetSubClusterPoliciesConfigurationsResponse response =
        stateStore.getPoliciesConfigurations(
            GetSubClusterPoliciesConfigurationsRequest.newInstance());

    Assert.assertNotNull(response);
    Assert.assertNotNull(response.getPoliciesConfigs());

    Assert.assertEquals(2, response.getPoliciesConfigs().size());

    Assert.assertTrue(response.getPoliciesConfigs()
        .contains(createSCPolicyConf("Queue1", "PolicyType1")));
    Assert.assertTrue(response.getPoliciesConfigs()
        .contains(createSCPolicyConf("Queue2", "PolicyType2")));
  }

  // Convenience methods

  private SubClusterInfo createSubClusterInfo(SubClusterId subClusterId) {

    String amRMAddress = "1.2.3.4:1";
    String clientRMAddress = "1.2.3.4:2";
    String rmAdminAddress = "1.2.3.4:3";
    String webAppAddress = "1.2.3.4:4";

    return SubClusterInfo.newInstance(subClusterId, amRMAddress,
        clientRMAddress, rmAdminAddress, webAppAddress, SubClusterState.SC_NEW,
        CLOCK.getTime(), "cabability");
  }

  private SubClusterPolicyConfiguration createSCPolicyConf(String queueName,
      String policyType) {
    return SubClusterPolicyConfiguration.newInstance(queueName, policyType,
        ByteBuffer.allocate(1));
  }

  private void addApplicationHomeSC(ApplicationId appId,
      SubClusterId subClusterId) throws YarnException {
    ApplicationHomeSubCluster ahsc =
        ApplicationHomeSubCluster.newInstance(appId, subClusterId);
    AddApplicationHomeSubClusterRequest request =
        AddApplicationHomeSubClusterRequest.newInstance(ahsc);
    stateStore.addApplicationHomeSubClusterMap(request);
  }

  private void setPolicyConf(String queue, String policyType)
      throws YarnException {
    SetSubClusterPolicyConfigurationRequest request =
        SetSubClusterPolicyConfigurationRequest
            .newInstance(createSCPolicyConf(queue, policyType));
    stateStore.setPolicyConfiguration(request);
  }

  private void registerSubCluster(SubClusterId subClusterId)
      throws YarnException {

    SubClusterInfo subClusterInfo = createSubClusterInfo(subClusterId);
    stateStore.registerSubCluster(
        SubClusterRegisterRequest.newInstance(subClusterInfo));
  }

  private SubClusterInfo querySubClusterInfo(SubClusterId subClusterId)
      throws YarnException {
    GetSubClusterInfoRequest request =
        GetSubClusterInfoRequest.newInstance(subClusterId);
    return stateStore.getSubCluster(request).getSubClusterInfo();
  }

  private SubClusterId queryApplicationHomeSC(ApplicationId appId)
      throws YarnException {
    GetApplicationHomeSubClusterRequest request =
        GetApplicationHomeSubClusterRequest.newInstance(appId);

    GetApplicationHomeSubClusterResponse response =
        stateStore.getApplicationHomeSubClusterMap(request);

    return response.getApplicationHomeSubCluster().getHomeSubCluster();
  }

  private SubClusterPolicyConfiguration queryPolicy(String queue)
      throws YarnException {
    GetSubClusterPolicyConfigurationRequest request =
        GetSubClusterPolicyConfigurationRequest.newInstance(queue);

    GetSubClusterPolicyConfigurationResponse result =
        stateStore.getPolicyConfiguration(request);
    return result.getPolicyConfiguration();
  }

}