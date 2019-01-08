/*
 * Copyright 1999-2015 dangdang.com.
 * <p>
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
 * </p>
 */

package com.dangdang.ddframe.job.lite.internal.schedule;

import com.dangdang.ddframe.job.lite.api.listener.ElasticJobListener;
import com.dangdang.ddframe.job.lite.config.LiteJobConfiguration;
import com.dangdang.ddframe.job.lite.internal.config.ConfigurationService;
import com.dangdang.ddframe.job.lite.internal.election.LeaderService;
import com.dangdang.ddframe.job.lite.internal.instance.InstanceService;
import com.dangdang.ddframe.job.lite.internal.listener.ListenerManager;
import com.dangdang.ddframe.job.lite.internal.monitor.MonitorService;
import com.dangdang.ddframe.job.lite.internal.reconcile.ReconcileService;
import com.dangdang.ddframe.job.lite.internal.server.ServerService;
import com.dangdang.ddframe.job.lite.internal.sharding.ExecutionService;
import com.dangdang.ddframe.job.lite.internal.sharding.ShardingService;
import com.dangdang.ddframe.job.reg.base.CoordinatorRegistryCenter;

import java.util.List;

/**
 * 为调度器提供内部服务的门面类.
 * 
 * @author zhangliang
 */
public final class SchedulerFacade {
    /**
     * 作业名称
     */
    private final String jobName;
    /**
     * 作业配置服务
     */
    private final ConfigurationService configService;
    /**
     * 直接点服务
     */
    private final LeaderService leaderService;
    /**
     * 服务器服务
     */
    private final ServerService serverService;
    /**
     * 作业运行实例
     */
    private final InstanceService instanceService;
    /**
     * 分片服务
     */
    private final ShardingService shardingService;
    /**
     * 执行作业
     */
    private final ExecutionService executionService;
    /**
     * 监控作业
     */
    private final MonitorService monitorService;
    /**
     * 调节作业不一致状态的服务
     */
    private final ReconcileService reconcileService;
    /**
     * 作业注册中心的监听器管理者
     */
    private ListenerManager listenerManager;
    
    public SchedulerFacade(final CoordinatorRegistryCenter regCenter, final String jobName) {
        this.jobName = jobName;
        configService = new ConfigurationService(regCenter, jobName);
        leaderService = new LeaderService(regCenter, jobName);
        serverService = new ServerService(regCenter, jobName);
        instanceService = new InstanceService(regCenter, jobName);
        shardingService = new ShardingService(regCenter, jobName);
        executionService = new ExecutionService(regCenter, jobName);
        monitorService = new MonitorService(regCenter, jobName);
        reconcileService = new ReconcileService(regCenter, jobName);
    }
    
    public SchedulerFacade(final CoordinatorRegistryCenter regCenter, final String jobName, final List<ElasticJobListener> elasticJobListeners) {
        this.jobName = jobName;
        configService = new ConfigurationService(regCenter, jobName);
        leaderService = new LeaderService(regCenter, jobName);
        serverService = new ServerService(regCenter, jobName);
        instanceService = new InstanceService(regCenter, jobName);
        shardingService = new ShardingService(regCenter, jobName);
        executionService = new ExecutionService(regCenter, jobName);
        monitorService = new MonitorService(regCenter, jobName);
        reconcileService = new ReconcileService(regCenter, jobName);
        listenerManager = new ListenerManager(regCenter, jobName, elasticJobListeners);
    }
    
    /**
     * 获取作业触发监听器.
     *
     * @return 作业触发监听器
     */
    public JobTriggerListener newJobTriggerListener() {
        return new JobTriggerListener(executionService, shardingService);
    }
    
    /**
     * 更新作业配置.
     *
     * @param liteJobConfig 作业配置
     * @return 更新后的作业配置
     */
    public LiteJobConfiguration updateJobConfiguration(final LiteJobConfiguration liteJobConfig) {
        configService.persist(liteJobConfig);
        return configService.load(false);
    }
    
    /**
     * 注册作业启动信息.
     * 
     * @param enabled 作业是否启用
     */
    public void registerStartUpInfo(final boolean enabled) {
        // 开启监听器
        listenerManager.startAllListeners();
        // 选举 主节点
        leaderService.electLeader();
        // 持久化 作业服务器的上线信息
        serverService.persistOnline(enabled);
        // 持久化 作业运行实例的线上信息
        instanceService.persistOnline();
        // 设置 需要重新分片的标记
        shardingService.setReshardingFlag();
        // 初始化 作业监听服务
        monitorService.listen();
        // 初始化 调节作业不一致状态的服务
        if (!reconcileService.isRunning()) {
            reconcileService.startAsync();
        }
    }
    
    /**
     * 终止作业调度.
     */
    public void shutdownInstance() {
        if (leaderService.isLeader()) {
            leaderService.removeLeader();
        }
        monitorService.close();
        if (reconcileService.isRunning()) {
            reconcileService.stopAsync();
        }
        JobRegistry.getInstance().shutdown(jobName);
    }
}
