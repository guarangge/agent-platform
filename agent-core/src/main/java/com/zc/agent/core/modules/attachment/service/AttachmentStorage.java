package com.zc.agent.core.modules.attachment.service;

import java.io.IOException;

/** 附件正文存储 SPI，业务层只持有 storageKey，不依赖具体对象存储。 */
public interface AttachmentStorage {

    /** 返回稳定的存储实现编码。 */
    String providerCode();

    /** 保存附件正文。 */
    void store(String storageKey, byte[] content) throws IOException;

    /** 读取附件正文。 */
    byte[] read(String storageKey) throws IOException;

    /** 删除附件正文，用于元数据写入失败时补偿。 */
    void delete(String storageKey) throws IOException;
}

