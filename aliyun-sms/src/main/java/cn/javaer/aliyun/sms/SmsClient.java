/*
 * Copyright (c) 2018 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.javaer.aliyun.sms;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendBatchSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendBatchSmsResponse;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teaopenapi.models.Config;
import com.google.gson.Gson;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import static cn.javaer.aliyun.sms.Utils.checkBatchSmsTemplate;
import static cn.javaer.aliyun.sms.Utils.checkNotEmpty;
import static cn.javaer.aliyun.sms.Utils.checkPhoneNumber;
import static cn.javaer.aliyun.sms.Utils.checkSmsResponse;
import static cn.javaer.aliyun.sms.Utils.checkSmsTemplate;


/**
 * 阿里云 SMS 客户端.
 *
 * @author cn-src
 */
public class SmsClient {

    private final Client client;
    private final Map<String, SmsTemplate> smsTemplates;
    private final Gson gson = new Gson();

    /**
     * Instantiates a new SmsClient.
     *
     * @param accessKeyId     阿里云短信 accessKeyId
     * @param accessKeySecret 阿里云短信 accessKeySecret
     */
    public SmsClient(final String accessKeyId, final String accessKeySecret) throws Exception {
        this(accessKeyId, accessKeySecret, Collections.emptyMap());
    }

    /**
     * Instantiates a new SmsClient.
     *
     * @param accessKeyId     阿里云短信 accessKeyId
     * @param accessKeySecret 阿里云短信 accessKeySecret
     * @param smsTemplates    预置短信模板
     */
    public SmsClient(final String accessKeyId,
                     final String accessKeySecret,
                     final Map<String, SmsTemplate> smsTemplates) throws Exception {
        checkNotEmpty(accessKeyId, "'accessKeyId' must be not empty");
        checkNotEmpty(accessKeySecret, "'accessKeySecret' must be not empty");
        Config config = new Config()
                .setAccessKeyId(accessKeyId)
                .setAccessKeySecret(accessKeySecret);
        config.endpoint = "dysmsapi.aliyuncs.com";
        this.client = new Client(config);
        this.smsTemplates = smsTemplates;
    }

    /**
     * Instantiates a new SmsClient.
     *
     * @param acsClient    IAcsClient
     * @param smsTemplates 预置短信模板
     */
    public SmsClient(final Client acsClient, final Map<String, SmsTemplate> smsTemplates) {
        this.client = acsClient;
        this.smsTemplates = smsTemplates;
    }

    /**
     * 发送短信验证码.
     *
     * @param phoneNumber 手机号码(中国)
     * @return 6 位数的随机码
     */
    public int sendVerificationCode(final String smsTemplateKey, final String phoneNumber) {
        checkPhoneNumber(phoneNumber);
        final SmsTemplate smsTemplate = this.smsTemplates.get(smsTemplateKey);
        Objects.requireNonNull(smsTemplate, () -> "SmsTemplate must be not null, key:" + smsTemplateKey);

        final int code = Utils.randomCode();
        smsTemplate.setTemplateParam(Collections.singletonMap("code", String.valueOf(code)));
        smsTemplate.setPhoneNumbers(Collections.singletonList(phoneNumber));
        send(smsTemplate);
        return code;
    }

    /**
     * 发送短信.
     *
     * @param smsTemplateKey 预置短信模板 key
     */
    public void send(final String smsTemplateKey) {
        final SmsTemplate smsTemplate = this.smsTemplates.get(smsTemplateKey);
        Objects.requireNonNull(smsTemplate, () -> "SmsTemplate must be not null, key:" + smsTemplateKey);

        send(smsTemplate);
    }

    /**
     * 发送短信.
     *
     * @param smsTemplateKey 预置短信模板 key
     * @param phoneNumbers   手机号码，优先于预置短信模板中配置的手机号码
     */
    public void send(final String smsTemplateKey, final String... phoneNumbers) {
        final SmsTemplate smsTemplate = this.smsTemplates.get(smsTemplateKey);
        Objects.requireNonNull(smsTemplate, () -> "SmsTemplate must be not null, key:" + smsTemplateKey);

        smsTemplate.setPhoneNumbers(Arrays.asList(phoneNumbers));
        send(smsTemplate);
    }

    /**
     * 发送短信.
     *
     * @param smsTemplate 短信模板
     */
    public void send(final SmsTemplate smsTemplate) {
        Objects.requireNonNull(smsTemplate);
        checkSmsTemplate(smsTemplate);
        SendSmsRequest request = new SendSmsRequest();
        request.setPhoneNumbers(String.join(",", smsTemplate.getPhoneNumbers()));
        request.setSignName(smsTemplate.getSignName());
        request.setTemplateCode(smsTemplate.getTemplateCode());
        request.setTemplateParam(Utils.toJsonStr(smsTemplate.getTemplateParam()));
        try {
            SendSmsResponse response = this.client.sendSms(request);
            checkSmsResponse(response);
        } catch (Exception e) {
            throw new SmsException(e);
        }
    }

    /**
     * 批量发送短信.
     *
     * <p>
     * 批量发送短信接口，支持在一次请求中分别向多个不同的手机号码发送不同签名的短信。
     * 手机号码，签名，模板参数字段个数相同，一一对应，短信服务根据字段的顺序判断发往指定手机号码的签名。
     *
     * <p>
     * 如果您需要往多个手机号码中发送同样签名的短信，请使用 {@link #send(SmsTemplate)}。
     *
     * @param batchSmsTemplate 批量发送短信模板
     */
    public void send(final BatchSmsTemplate batchSmsTemplate) {
        Objects.requireNonNull(batchSmsTemplate);
        checkBatchSmsTemplate(batchSmsTemplate);
        SendBatchSmsRequest request = new SendBatchSmsRequest();
        request.setPhoneNumberJson(this.gson.toJson(batchSmsTemplate.getPhoneNumbers()));
        request.setSignNameJson(this.gson.toJson(batchSmsTemplate.getSignNames()));
        request.setTemplateCode(batchSmsTemplate.getTemplateCode());
        request.setTemplateParamJson(this.gson.toJson(batchSmsTemplate.getTemplateParams()));
        try {
            SendBatchSmsResponse response = this.client.sendBatchSms(request);
            checkSmsResponse(response);
        } catch (Exception e) {
            throw new SmsException(e);
        }
    }
}
