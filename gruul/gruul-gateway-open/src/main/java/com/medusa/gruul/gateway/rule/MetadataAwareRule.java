

package com.medusa.gruul.gateway.rule;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.system.HostInfo;
import cn.hutool.system.SystemUtil;
import com.medusa.gruul.common.core.support.RibbonRuleProperties;
import com.medusa.gruul.common.core.util.SpringContextHolder;
import com.medusa.gruul.gateway.predicate.AbstractDiscoveryEnabledPredicate;
import com.medusa.gruul.gateway.predicate.GrayMetadataAwarePredicate;
import com.netflix.loadbalancer.Server;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.PatternMatchUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ribbon 路由规则器
 *
 * @author dream.lu
 */
@Slf4j
public class MetadataAwareRule extends AbstractDiscoveryEnabledRule {

	public MetadataAwareRule() {
		this(new GrayMetadataAwarePredicate());
	}

	public MetadataAwareRule(AbstractDiscoveryEnabledPredicate predicate) {
		super(predicate);
	}

	@Override
	public List<Server> filterServers(List<Server> serverList) {
		RibbonRuleProperties ribbonProperties = SpringContextHolder.getBean(RibbonRuleProperties.class);
		List<String> priorIpPattern = ribbonProperties.getPriorIpPattern();

		if (ribbonProperties.isGrayEnabled()) {
			return serverList;
		}

		// 1. 查找是否有本机 ip
		HostInfo hostInfo = SystemUtil.getHostInfo();
		String hostIp = hostInfo.getAddress();

		// 优先的 ip 规则
		boolean hasPriorIpPattern = !priorIpPattern.isEmpty();
		String[] priorIpPatterns = priorIpPattern.toArray(new String[0]);

		List<Server> priorServerList = new ArrayList<>();
		for (Server server : serverList) {
			String host = server.getHost();
			// 2. 优先本地 ip 的服务
			if (!hasPriorIpPattern && ObjectUtil.equal(hostIp, host)) {
				log.warn("{} 不存在优先配置，本地路由条件", hostIp);
				return Collections.singletonList(server);
			}
			// 3. 优先的 ip 服务
			if (hasPriorIpPattern && PatternMatchUtils.simpleMatch(priorIpPatterns, host)) {
				log.warn("{} 存在优先配置，ribbon 强制路由", priorIpPatterns);
				priorServerList.add(server);
			}
		}

		// 4. 如果优先的有数据直接返回
		if (!priorServerList.isEmpty()) {
			return priorServerList;
		}

		return Collections.unmodifiableList(serverList);
	}

}
