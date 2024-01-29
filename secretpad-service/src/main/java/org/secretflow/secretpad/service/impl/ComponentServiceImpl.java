/*
 * Copyright 2023 Ant Group Co., Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.secretflow.secretpad.service.impl;

import org.secretflow.secretpad.common.errorcode.GraphErrorCode;
import org.secretflow.secretpad.common.exception.SecretpadException;
import org.secretflow.secretpad.common.util.FileUtils;
import org.secretflow.secretpad.common.util.JsonUtils;
import org.secretflow.secretpad.common.util.ProtoUtils;
import org.secretflow.secretpad.service.ComponentService;
import org.secretflow.secretpad.service.configuration.SecretpadComponentConfig;
import org.secretflow.secretpad.service.constant.ComponentConstants;
import org.secretflow.secretpad.service.model.graph.CompListVO;
import org.secretflow.secretpad.service.model.graph.ComponentKey;
import org.secretflow.secretpad.service.model.graph.ComponentSummaryDef;
import org.secretflow.secretpad.service.model.graph.GraphNodeInfo;

import com.secretflow.spec.v1.CompListDef;
import com.secretflow.spec.v1.ComponentDef;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.secretflow.proto.pipeline.Pipeline;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Component service implementation class
 *
 * @author yansi
 * @date 2023/6/7
 */
@Slf4j
@Service
public class ComponentServiceImpl implements ComponentService {

    @Value("${component.i18n.location:./config/i18n}")
    private String i18nLocation;

    @Autowired
    private List<CompListDef> components;

    @Resource
    private SecretpadComponentConfig secretpadComponentConfig;

    @Override
    public Map<String, CompListVO> listComponents() {
        Map<String, CompListVO> resp = new HashMap<>();
        components.forEach(compListDef -> {
            List<ComponentDef> comps = compListDef.getCompsList();
            if (!CollectionUtils.isEmpty(comps)) {
                CompListVO compListVO = CompListVO.builder()
                        .name(compListDef.getName())
                        .version(compListDef.getVersion())
                        .desc(compListDef.getDesc())
                        .comps(new ArrayList<>()).build();
                compListVO.getComps().addAll(comps.stream()
                        .map(componentDef -> {
                            //secretflow/domain/name:version
                            String hide = compListDef.getName() + "/" + componentDef.getDomain() + "/" + componentDef.getName() + ":" + componentDef.getVersion();
                            if (secretpadComponentConfig.getHide().contains(hide)) {
                                log.info("hide {}", hide);
                                SF_HIDE_COMPONENTS.put(componentDef.getName(), componentDef);
                                return null;
                            }
                            return ComponentSummaryDef.builder()
                                    .domain(componentDef.getDomain())
                                    .name(componentDef.getName())
                                    .version(componentDef.getVersion())
                                    .desc(componentDef.getDesc())
                                    .build();
                        })
                        .filter(Objects::nonNull).toList());
                resp.put(compListVO.getName(), compListVO);
            }
        });
        resp.remove(ComponentConstants.SECRETPAD);
        return resp;
    }

    @Override
    public ComponentDef getComponent(ComponentKey key) {
        return batchGetComponent(List.of(key)).get(0);
    }

    @Override
    public List<ComponentDef> batchGetComponent(List<ComponentKey> keys) {
        List<ComponentDef> result = new ArrayList<>();
        Map<ComponentKey, ComponentDef> componentMap = new HashMap<>();
        components.stream().filter(compListDef -> !CollectionUtils.isEmpty(compListDef.getCompsList())).forEach(compListDef -> {
            compListDef.getCompsList().stream().forEach(componentDef -> {
                componentMap.put(new ComponentKey(compListDef.getName(), componentDef.getDomain(), componentDef.getName()), componentDef);
            });
        });
        if (!CollectionUtils.isEmpty(keys)) {
            keys.forEach(key -> {
                if (!componentMap.containsKey(key)) {
                    throw SecretpadException.of(GraphErrorCode.COMPONENT_NOT_EXISTS, key.toString());
                }
                result.add(componentMap.get(key));
            });
        }
        return result;
    }

    @Override
    public Object listComponentI18n() {
        Map<String, Map<String, Object>> config = new HashMap<>();
        Map<String, Object> secretpad = new HashMap<>();
        try {
            File dir = ResourceUtils.getFile(i18nLocation);
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    String fileName = file.getName();
                    String app = fileName.substring(0, fileName.lastIndexOf('.'));
                    String str = FileUtils.readFile2String(file);
                    Map<String, Object> content = JsonUtils.toJavaMap(str, Object.class);
                    if (!CollectionUtils.isEmpty(content)) {
                        if (app.equals(ComponentConstants.SECRETPAD)) {
                            secretpad = content;
                        } else {
                            config.put(app, content);
                        }
                    }
                }
                Map<String, Object> finalSecretpad = secretpad;
                config.keySet().forEach(k -> config.get(k).putAll(finalSecretpad));
            }
        } catch (IOException e) {
            throw SecretpadException.of(GraphErrorCode.COMPONENT_18N_ERROR, e);
        }
        config.keySet().forEach(key -> {
            Map<String, Object> map = config.get(key);
            Iterator<Map.Entry<String, Object>> iterator = map.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Object> entry = iterator.next();
                String hide = key + "/" + entry.getKey();
                if (secretpadComponentConfig.getHide().contains(hide)) {
                    log.info("hide {}", hide);
                    iterator.remove();
                }
            }
        });
        return config;
    }

    @Override
    public boolean isSecretpadComponent(GraphNodeInfo node) {
        String domain, name;
        Pipeline.NodeDef pipelineNodeDef;
        if (node.getNodeDef() instanceof Pipeline.NodeDef) {
            pipelineNodeDef = (Pipeline.NodeDef) node.getNodeDef();
        } else {
            Pipeline.NodeDef.Builder nodeDefBuilder = Pipeline.NodeDef.newBuilder();
            pipelineNodeDef = (Pipeline.NodeDef) ProtoUtils.fromJsonString(JsonUtils.toJSONString(node.getNodeDef()), nodeDefBuilder);
        }
        domain = pipelineNodeDef.getDomain();
        name = pipelineNodeDef.getName();
        return ComponentConstants.READ_DATA.equals(domain) && ComponentConstants.DATA_TABLE.equals(name);
    }

}
