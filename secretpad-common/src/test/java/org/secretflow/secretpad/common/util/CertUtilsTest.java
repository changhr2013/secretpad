/*
 * Copyright 2024 Ant Group Co., Ltd.
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

package org.secretflow.secretpad.common.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.cert.CertificateException;

/**
 * @author yutu
 * @date 2024/04/10
 */
public class CertUtilsTest {

    @Test
    void testLoadX509Cert_withInvalidFilePath() {
        String filepath = "invalid/file/path";
        Assertions.assertThrows(IOException.class, () -> {
            CertUtils.loadX509Cert(filepath);
        });
    }

    @Test
    void testLoadX509Cert_withInvalidCertificate() {
        String filepath = "classpath:./a/a.txt";
        Assertions.assertThrows(CertificateException.class, () -> {
            CertUtils.loadX509Cert(filepath);
        });
    }

    @Test
    void testLoadX509Cert_withEmptyFilePath() {
        String filepath = "";
        Assertions.assertThrows(IOException.class, () -> {
            CertUtils.loadX509Cert(filepath);
        });
    }

}