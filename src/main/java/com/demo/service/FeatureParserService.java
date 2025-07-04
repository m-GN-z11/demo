package com.demo.service;

import com.demo.config.FeatureProperties;
import com.demo.dto.FeatureDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 特征解析服务类
 * 用于从文件中解析特征数据。
 */
@Service
public class FeatureParserService {
    // 日志记录器，用于记录运行时信息、警告和错误。
    private static final Logger logger = LoggerFactory.getLogger(FeatureParserService.class);

    // 特征定义列表，存储从配置中加载的特征定义。
    private final List<FeatureDefinition> featureDefinitions;

    /**
     * 构造函数，通过依赖注入初始化特征定义列表。
     * @param featureProperties 包含特征定义的配置对象。
     */
    @Autowired
    public FeatureParserService(FeatureProperties featureProperties) {
        // 从配置对象中获取特征定义列表。
        this.featureDefinitions = featureProperties.getDefinitions();
        // 检查特征定义列表是否为空或未初始化，并记录警告日志。
        if (this.featureDefinitions == null || this.featureDefinitions.isEmpty()) {
            logger.warn("未能从配置文件加载任何特征定义！");
        }
    }

    /**
     * 从指定文件路径解析特征数据。
     * @param filePath 特征文件的路径。
     * @return 包含所有解析特征的映射，键为特征名称，值为特征值列表。
     * @throws IOException 如果文件读取或解析过程中发生IO异常。
     */
    public Map<String, List<? extends Number>> parseFeatureFile(String filePath) throws IOException {
        // 使用LinkedHashMap保持特征名称的插入顺序。
        Map<String, List<? extends Number>> allParsedFeatures = new LinkedHashMap<>();

        try (FileInputStream fis = new FileInputStream(filePath);
             DataInputStream dis = new DataInputStream(fis)) {

            // 读取文件前4个字节，解析为帧数（numFrames）。
            byte[] buffer4Bytes = new byte[4];
            if (dis.read(buffer4Bytes) < 4) {
                throw new IOException("无法从文件读取 num_frames (文件过短): " + filePath);
            }
            // 使用ByteBuffer解析字节数组为整数，小端序。
            int numFrames = ByteBuffer.wrap(buffer4Bytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
            logger.info("特征文件 '{}' 包含 {} 帧的数据。", filePath, numFrames);

            // 如果帧数为0或负数，返回空的特征集，并记录警告日志。
            if (numFrames <= 0) {
                logger.warn("特征文件 '{}' 中没有有效的数据帧 (numFrames = {})。将返回空的特征集。", filePath, numFrames);
                for (FeatureDefinition def : this.featureDefinitions) {
                    if (def.getTypeChar() == 'f') {
                        allParsedFeatures.put(def.getName(), new ArrayList<Float>());
                    } else if (def.getTypeChar() == 'i') {
                        allParsedFeatures.put(def.getName(), new ArrayList<Integer>());
                    }
                }
                return allParsedFeatures;
            }

            // 遍历每个特征定义，解析对应的特征数据。
            for (FeatureDefinition featureDef : this.featureDefinitions) {
                String featureName = featureDef.getName();
                char typeChar = featureDef.getTypeChar();
                int typeSize = featureDef.getTypeSize();

                // 根据特征类型（float或int）解析数据。
                if (typeChar == 'f') {
                    List<Float> values = new ArrayList<>(numFrames);
                    for (int i = 0; i < numFrames; i++) {
                        if (dis.read(buffer4Bytes) < typeSize) {
                            throw new IOException("读取特征 '" + featureName + "' (float) 的第 " + i + " 帧数据时文件意外结束。");
                        }
                        // 解析4字节为float，小端序。
                        values.add(ByteBuffer.wrap(buffer4Bytes).order(ByteOrder.LITTLE_ENDIAN).getFloat());
                    }
                    allParsedFeatures.put(featureName, values);
                } else if (typeChar == 'i') {
                    List<Integer> values = new ArrayList<>(numFrames);
                    for (int i = 0; i < numFrames; i++) {
                        if (dis.read(buffer4Bytes) < typeSize) {
                            throw new IOException("读取特征 '" + featureName + "' (int) 的第 " + i + " 帧数据时文件意外结束。");
                        }
                        // 解析4字节为int，小端序。
                        values.add(ByteBuffer.wrap(buffer4Bytes).order(ByteOrder.LITTLE_ENDIAN).getInt());
                    }
                    allParsedFeatures.put(featureName, values);
                } else {
                    // 如果特征类型不支持，记录警告日志并跳过该特征。
                    logger.warn("特征 '{}' 定义了不支持的类型字符 '{}'。该特征将被跳过。", featureName, typeChar);
                }
            }
            // 记录成功解析的特征数量和帧数。
            logger.info("成功从 '{}' 解析了 {} 个特征 (共 {} 帧数据)。", filePath, allParsedFeatures.size(), numFrames);

        } catch (IOException e) {
            // 捕获并记录IO异常，然后重新抛出。
            logger.error("解析特征文件 '{}' 时发生IO异常: {}", filePath, e.getMessage(), e);
            throw e;
        }
        return allParsedFeatures;
    }
}
