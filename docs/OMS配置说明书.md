# OMS配置说明书

| | |
|---|---|
| 版本 | V1.0.1.26 |
| 文件状态 | 送审 |
| 项目名称 | 网元管理模块 |
| 拟制部门 | 运营商业务部信息安全业务开发部 |

| | 姓名 | 时间 |
|---|---|---|
| 拟制 | 杨学康 | 2026-01-30 |
| 审核 | | |
| 批准 | | |

中国.杭州

## 版本历史及修订

| 版本 | 日期 | 拟制人/修订人 | 版本修订说明 | 审核人 |
|---|---|---|---|---|
| | 2023-11-03 | 周海青 | 初始版本提交 | |
| V1.0.1.19 | 2025-04-09 | 杨学康 | 更新版本提交 | |
| V1.0.1.20 | 2025-05-16 | 宣呈辉 | 更新版本提交 | |
| V1.0.1.21 | 2025-07-03 | 杨学康 | 更新版本提交 | |
| V1.0.1.22 | 2025-08-12 | 杨学康 | 更新版本提交 | |
| V1.0.1.23 | 2025-09-19 | 杨学康 | 更新版本提交 | |
| V1.0.1.24 | 2025-10-15 | 杨学康 | 更新版本提交 | |
| V1.0.1.25 | 2025-11-20 | 杨学康 | 更新版本提交 | |
| V1.0.1.26 | 2026-01-30 | 杨学康 | 更新版本提交 | |

## 目录

- 1 application配置文件
- 2 application-dev配置文件
  - 2.1 Server：oms服务端配置
  - 2.2 logging：日志配置
  - 2.3 mybatis：mybatis配置
  - 2.4 localftp：本地ftp配置
  - 2.5 data-source：多数据源配置
  - 2.6 kafka：kafka配置
  - 2.7 zookeeper：zookeeper配置
  - 2.8 bureaudata：bureaudata功能相关配置
  - 2.9 falcomm：巡检配置
  - 2.10 license：license配置
  - 2.11 log-record：日志相关配置
  - 2.12 oms：oms相关配置
  - 2.13 omc：omc通道配置
  - 2.14 icp：icp断连的告警编码
  - 2.15 faultTrasfer：故障传递配置
  - 2.16 moduleconfig：获取指定模块的配置相关配置
  - 2.17 performance：性能文件配置
  - 2.18 customized-security：安全相关配置
  - 2.19 LSN：LSN配置
  - 2.20 software：软件包管理配置
  - 2.21 vnf：虚层交互配置
  - 2.22 prometheus：prometheus配置
  - 2.23 forest：forest配置
  - 2.24 cf：cf配置
  - 2.25 signaling：信令配置
  - 2.26 alarm：告警配置
  - 2.27 datagather：性能数据采集配置（不需要可不配置，主要是为了四川运维平台开发的）
  - 2.28 module-back：模块备份配置
  - 2.29 resource：资源文件位置配置
  - 2.30 nacos：nacos配置
  - 2.31 moduleconfig：可查询配置的模块
  - 2.32 uploadtoftp性能文件上传配置（不需要可不配置）
  - 2.33 thresholdalarm：性能阈值告警
  - 2.34 redis：redis配置
  - 2.35 nginx-weight-control：nginx权重配置（omc软件升级自动化流程用到）
  - 2.36 instruction：容灾校验密码
  - 2.37 connectionquerymodule：网元连接查询
  - 2.38 exceptionpolicyreconciliation：高频专用配置（其他网元可忽略），异常策略数据对账
  - 2.39 business：业务日志相关配置
  - 2.40 pmperiodmonitor：指标颗粒度阈值监控
  - 2.41 auth：网元直连鉴权配置
  - 2.42 generalconfig：通用配置功能
  - 2.43 interception：垃圾短信拦截配置功能
- 3 script配置文件
  - 3.1 Scriptconfig 脚本配置
- 4 VNFConfig配置文件（只有网元新建的时候需要配置）
  - 4.1 vnfconfig网元虚机配置
- 5 application-lsfixed配置文件
  - 5.1 licensefixed：license固定配置部分
- 6 application-performancefixed：性能统计固定配置
  - 6.1 performancefixed：统计固定配置
- 7 performanceBlock：性能容灾配置
- 8 application-inspection：作业指令配置
- 9 application-dataStorage:通用数据操作配置
  - 9.1 Storage 通用数据存储

---

## 1 application配置文件

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| fastconfig.period | 刷新配置间隔 | 可选 | 默认10s |
| fastconfig.configPathList | 需要刷新的配置文件列表，目前只配置classpath:performanceBlock.yml | 可选 | 默认空列表 |
| spring.profiles.include | 指定要额外激活的配置文件，目前的配置有：dev,performancefixed,lsfixed,inspection | 必选 | |
| mvc.servlet.load-on-startup | servlet加载优先级设置-启动就加载 | 必选 | |
| main.allow-bean-definition-overriding | 是否允许定义重名的bean对象覆盖原有的bean | 必选 | |

## 2 application-dev配置文件

### 2.1 Server：oms服务端配置

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| port | Oms启动端口，jar包启动时生效 | 可选 | Tomcat启动时此配置无用，jar包启动时必选，默认值8080 |
| servle.context-path | Oms基础访问路径，jar包启动时生效 | 可选 | Tomcat启动时此配置无用，jar包启动影响访问路径，默认值/ |
| ssl.key-store | oss提供的证书文件路径 | 可选 | Tomcat启动时此配置无用，jar包启动影响访问路径，默认值\*/oms.p12 |
| ssl.key-store-password | oss提供的证书密码 | 可选 | Tomcat启动时此配置无用，jar包启动影响访问路径，默认值eastcom2024 |
| ssl.key-alias | oss提供的证书别名 | 可选 | Tomcat启动时此配置无用，jar包启动影响访问路径，默认值eastcom |
| ssl.key-store-type | oss提供的证书类型 | 可选 | Tomcat启动时此配置无用，jar包启动影响访问路径，默认值PKCS12 |

### 2.2 logging：日志配置

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| config | logback配置文件路径 | 可选 | 默认classpath:logback-spring.xml |

### 2.3 mybatis：mybatis配置

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| mapper-locations | 实体类数据表映射文件路径(opengauss映射opengauss路径) | 必选 | |
| config-location | mybatis配置文件路径(opengauss映射opengauss路径) | 必选 | |

### 2.4 localftp：本地ftp配置

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| localftp-username | 本地ftp帐号 | 可选 | admin |
| localftp-password | 本地ftp密码 | 可选 | 123456 |
| localftp-port | 本地ftp端口 | 可选 | 22 |
| localftp-ip | 本地ftp ip | 可选 | 127.0.0.1 |

### 2.5 data-source：多数据源配置

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| druid-data-sources.default.name | 数据源名 | 必选 | |
| druid-data-sources.default.url | 数据源url(opengauss映射opengauss路径) | 必选 | |
| druid-data-sources.default.username | 数据库登录名 | 必选 | |
| druid-data-sources.default.password | 数据库登录密码 | 必选 | |
| druid-data-sources.default.driveClassName | 数据库驱动（opengauss映射opengauss路径） | 必选 | |
| druid-data-sources.default.initialSize | 连接池初始化连接数 | 必选 | |
| druid-data-sources.default.minIdle | 最小空闲连接 | 必选 | |
| druid-data-sources.default.maxActive | 最大连接数 | 必选 | |
| druid-data-sources.default..maxWait | 配置获取连接等待超时的时间 | 必选 | |
| druid-data-sources.default.timeBetweenEvictionRunsMillis | 配置间隔多久才进行一次检测，检测需要关闭的空闲连接，单位是毫秒 | 必选 | |
| druid-data-sources.default.minEvictableIdleTimeMillis | 配置一个连接在池中最小生存的时间，单位是毫秒 | 必选 | |
| druid-data-sources.default.validationQuery | 在连接池返回连接给调用者前用来对连接进行验证的查询 SQL，要求为一条查询语句 | 必选 | |
| druid-data-sources.default.poolPreparedStatements | 是否缓存preparedStatement，也就是PSCache。PSCache对支持游标的数据库性能提升巨大，比如说oracle。在mysql下建议关闭 | 必选 | |
| druid-data-sources.default.maxPoolPreparedStatementPerConnectionSize | 指定每个连接上PSCache的大小 | 必选 | |
| druid-data-sources.default.filters | 配置监控统计拦截的filters | 必选 | |

### 2.6 kafka：kafka配置

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| bootstrap-servers | Kafka地址 | 必选 | |
| acks | 消息发送的确认级别，关系消息可靠性。`-1`(或 all)：最高可靠性，需所有同步副本(ISR)确认，务必在Broker端设置 min.insync.replicas（如2）；`1`：平衡选择，仅需Leader确认；`0`：性能最佳，但可能丢失消息 | 可选 | 默认值为-1 |
| group-id | 消费者组id | 可选 | 默认值为1111 |
| retries | 生产者可以重发消息的次数，如果达到这个次数，生产者会放弃重试并返回错误。默认情况下，生产者会在每次重试之间等待100ms，可以通过retry.backoff.ms参数来设置时间间隔 | 可选 | 默认值为0，即失败后不重试 |
| key-serializer | key序列化设置 | 可选 | 默认值为org.apache.kafka.common.serialization.StringSerializer |
| value-serializer | value序列化设置 | 可选 | 默认值为org.apache.kafka.common.serialization.StringSerializer |
| key-deserializer | key反序列设置 | 可选 | 默认值为org.apache.kafka.common.serialization.StringDeserializer |
| value-deserializer | value反序列化设置 | 可选 | 默认值为org.apache.kafka.common.serialization.StringDeserializer |
| enable-auto-commit | 是否开启自动提交消费位移（offset） | 可选 | 默认值为true |
| max-poll-records | 控制消费者单次拉取请求返回的最大消息数 | 可选 | 默认值为500 |
| security-protocol | 定义了客户端与Kafka Broker通信时使用的整体安全协议 | 可选 | Kafka没有开启sasl认证可不填 |
| sasl-mechanism | 指定了SASL框架下的具体认证机制 | 可选 | Kafka没有开启sasl认证可不填 |
| sasl-jaas-config | 提供了PLAIN机制所需的具体登录凭证。其格式是固定的，需要替换 your_username和 your_password为Kafka管理员分配给你的实际账号和密码 | 可选 | Kafka没有开启sasl认证可不填 |

### 2.7 zookeeper：zookeeper配置

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| url | ZooKeeper服务器的地址和端口。如果是集群，可配置多个地址（逗号分隔） | 必选 | |
| intervalTime | 重试间隔，单位毫秒 | 可选 | 默认值为5000 |
| reconnectionStrategy | 连接断开后的重连策略最大重试次数 | 可选 | 默认值为3 |
| session-timeout-ms | 会话超时时间，单位毫秒 | 可选 | 默认值为5000 |
| connection-timeout-ms | 建立TCP连接时的最大等待时间，超过则失败，单位毫秒 | 可选 | 默认值为30000 |
| acl-ip-list | 授权ip地址列表，用于配置IP白名单 | 可选 | 默认为null，即全部白名单 |

### 2.8 bureaudata：bureaudata功能相关配置

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| ftpMode | ftp模式1表示主动0表示被动，仅ftp用，sftp不会用 | 可选 | 默认值为1 |
| ftp-host | 局数据ftp或者sftp 地址 | 可选 | |
| ftp-username | 局数据ftp或者sftp 账号 | 可选 | |
| ftp-password | 局数据ftp或者sftp密码 | 可选 | |
| ftp-port | 局数据ftp或者sftp 端口 | 可选 | |
| ftpPath | 局数据ftp或者sftp文件路径 | 可选 | |
| localPath | 局数据本地路径 | 可选 | /utxt/soft/ |
| ftpOrSftp | 局数据下载连接的远程服务类型ftp/sftp | 可选 | 默认值为ftp |
| initialDelay | 局数据定时访问nacos时延 | 可选 | 默认值为1 |
| period | 局数据定时访问nacos时间间隔 | 可选 | 默认值为20 |
| maxModNum | 局数据每次访问nacos最大修改数量 | 可选 | 默认值为50 |

### 2.9 falcomm：巡检配置

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| scriptEncoding | 脚本编码 | 可选 | 默认值为UTF-8 |
| scriptPath | 脚本路径 | 可选 | 默认/home/sms/check_sh/datecheck.sh |

### 2.10 license：license配置

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| ftp-username | ftp帐号 | 可选 | root |
| ftp-password | ftp密码 | 可选 | eastcom |
| ftp-port | ftp端口 | 可选 | 22 |
| uploadpathOms | 存放需上传文件路径 | 可选 | D:/Sftp/license/ |
| loadpathcf.SLF.uploadpathCf | cf端路径 | 可选 | E:/Desk/Sftp/package/111 |
| loadpathcf.SIF.uploadpathCf | cf端路径 | 可选 | E:/Desk/Sftp/package/222 |

### 2.11 log-record：日志相关配置

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| logRecordBackupFilePath | 日志备份路径 | 可选 | /home/personal/logrecord/ |
| logRecordBusiErrLogFilePath | 业务失败日志路径 | 可选 | /home/errorlog/ |
| logRecordRemoteFtpOrSftp | 日志远程输出类型ftp/sftp | 可选 | 默认值为ftp |
| logRecordSyslogHost | syslog主机ip | 可选 | 127.0.0.1 |
| logRecordSyslogPort | syslog端口 | 可选 | 3237 |
| logRecordTimeFormat | 日志时间格式（yyyy-MM-dd-HH-mm代表按分钟，yyyy-MM-dd-HH代表按小时，yyyy-MM-dd代表按天） | 可选 | 默认值为yyyy-MM-dd |
| logRecordFileSizeUnit | 日志保存大小单位（KB,MB两个值，注意要大写） | 可选 | 默认值为MB |
| logRecordMaxSaveTime | 日志保存最小时间（最少保留多少时间，单位由logback日志fileAll的appender中FileNamePattern决定），单位天 | 可选 | 默认值为90 |
| nePMLogFilePath | 网元性能测量传递事件的sftp日志目录 | 可选 | 默认值为/var/log |
| nePMLogFileName | 网元性能测量传递事件日志文件名(不包含时间），即sftp日志文件名称 | 可选 | 默认值为xferlog |
| nePMLogTriggerCron | 定时扫描cron表达式 | 可选 | 默认值为`0 1 0 * * ?`，每天凌晨1点 |

### 2.12 oms：oms相关配置

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| version | oms版本号(不需要修改) | 必选 | |
| omsMid | mid | 必选 | |
| omsFid | fid | 必选 | |
| config.backup | 日志备份路径 | 可选 | /home/backup |
| readerIdleTimeOut | 读取空闲时间(秒) | 可选 | 默认值为3 |
| writerIdleTimeOut | 写入空闲时间(秒) | 可选 | 默认值为3 |
| allIdleTimeOut | 全局空闲时间(秒) | 可选 | 默认值为3 |
| defultTimeOutWithModule | 模块默认超时时间(秒) | 可选 | 默认值为5 |
| heartBeatLimitWithModule | 模块心跳限制时间(毫秒) | 可选 | 默认值为10000 |

### 2.13 omc：omc通道配置

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| account | omc账户列表，支持配置多个 | 必选 | |
| account.username | 帐号 | 必选 | |
| account.password | 密码 | 必选 | |
| port | 端口 | 必选 | |
| loginReset | 登录重置 | 可选 | 默认值为false |
| timeout | 超时时间 | 可选 | 默认值为5000 |

### 2.14 icp：icp断连的告警编码

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| close | icp断连告警的alarmCode | 可选 | 默认值为0007 |

### 2.15 faultTrasfer：故障传递配置

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| enable | 是否启用故障传递 | 可选 | 默认false |
| blockPercent | 表示阻塞百分比，一旦故障业务的百分比达到这个值，会去阻塞另一个业务使其数据阻塞，两个值相加最好大于1 | 可选 | 默认值为1 |
| restorePercent | 表示恢复百分比，只有业务恢复的百分比达到这个值，才会去恢复另一个业务使其数据不阻塞 | 可选 | 默认值为0.1 |
| moduleRelation | 表示故障模块和需要阻塞业务的模块编号的关系，比如100:5,1 表示SLF故障 阻塞vSG(ESIP)和vSG(vSCU) 模块，多组之间用分号隔开 | 可选 | 默认值为100:5,1 |

### 2.16 moduleconfig：获取指定模块的配置相关配置

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| modulelist | 模块名，大写，多个模块之间用逗号隔开 | 可选 | 默认值为SIF,VSCU,ESIP,MSIP |

### 2.17 performance：性能文件配置

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| filepath | 性能统计文件统计输出位置 | 可选 | 默认值为/utxt/soft/performancedata |
| reportPeriod | | 可选 | 默认值为30 |
| businessType | 网元类型 | 必选 | 默认值为CMCCPSPS5 |
| timeInternal.SystemLoad | 系统加载时间 | 可选 | 默认值为30 |
| timeInternal.default | 默认事件 | 可选 | 默认值为900 |
| deletePeriod | 统计文件定时删除保留天数 | 可选 | 默认值为15 |
| meload.NCP | Meload.网元名，后面填cpu占用率（系统负荷）从哪几个模块获取 | 可选 | 默认值为VSS,SLF |
| meload.CNE | Meload.网元名，后面填cpu占用率（系统负荷）从哪几个模块获取 | 可选 | 默认值为ESIP,SIF |
| meload.PSPA | Meload.网元名，后面填cpu占用率（系统负荷）从哪几个模块获取 | 可选 | 默认值为SIF |
| reportUrl | 性能指标上报URL | 可选 | |
| reportTasks | 需要上报的任务名称列表，只有匹配列表里的任务才进行HTTP上报 | 可选 | |

### 2.18 customized-security：安全相关配置

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| security-appkey | 东信软件登录本系统的appkey | 可选 | 默认值为123456 |
| securityTokenExpired | 登录有效期（分钟） | 可选 | 默认值为30 |
| securityLoginBindIp | 登录是否绑定ip，true表示1个账号只能一个ip登录，其他ip登录就踢掉当前登录的ip | 可选 | 默认值为false |
| AES-CBC-PKCS5Padding-skey | 解密秘钥，如果不是base64编码此字符串length为16，isBase64参数设为0，如果是base64编码则isBase64设为1 | 可选 | 默认值为eastcom123411111 |
| AES-CBC-PKCS5Padding-skey-is-Base64 | 解密秘钥是否base64编码 | 可选 | 默认值为false |
| AES-CBC-PKCS5Padding-encoding | 字符类型 | 可选 | 默认值为utf-8 |
| AES-CBC-PKCS5Padding-ivparameter | 解密向量 | 可选 | 默认值为1234eastcom11111 |
| httpclient-oms-url | 本应用的访问链接，配置示例：httpclient.oms.url = http://10.8.22.38:8081/oms，不配置则默认从请求中取出此值 | 可选 | 默认值为null，即从请求中取出此值 |

### 2.19 LSN：LSN配置

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| LSN | \*\*\* | 可选 | 默认值为LSN |

### 2.20 software：软件包管理配置

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| ftp-username | ftp帐号 | 可选 | 123456 |
| ftp-password | ftp密码 | 可选 | 123456 |
| ftp-port | ftp端口 | 可选 | 22 |
| localpath | 本地路径 | 可选 | /home/gpfsr/slfs-1/ |
| rename.VSCU.name | 软件包拷贝目标名(支持按实例名来指定，如VSCU-21) | 可选 | / |
| rename.VSCU.dstpath | 软件包拷贝目标目录 | 可选 | /home/gpfsr/slfs-1/webapps/ |
| midscriptspath | mid脚本路径（调用中间脚本所在目录） | 可选 | /home/gpfsr/commons/ |

### 2.21 vnf：虚层交互配置

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| http.connect-timeout | 连接超时时间，单位毫秒 | 可选 | 默认值为5000 |
| http.request-timeout | 从connectManager获取Connection超时时间，单位毫秒 | 可选 | 默认值为200 |
| http.socket-timeout | 请求获取数据的超时时间，单位毫秒 | 可选 | 默认值为5000 |
| http.keep-timeout | 保持连接超时时间 | 可选 | 默认值为30000 |
| http.max-total | 最大连接总数 | 可选 | 默认值为300 |
| http.default-max-per-rout | 连接池中每个路由（route）的最大连接数限制 | 可选 | 默认值为300 |
| https.client-store-path | 客户端证书路径 | 可选 | /utxt/sslClient.p12 |
| https.client-store-password | 客户端证书密码 | 可选 | eastcom |
| nfv-api | nfv接口类型，1：一期接口，2：二期接口 | 可选 | 2 |
| token-expire-date | token过期时间，单位秒 | 可选 | 默认值为36000 |
| mano-username | VNFM账号 | 可选 | vnfm |
| mano-password | VNFM密码 | 可选 | 123456 |
| mano-protocol | VNFM的协议类型，可配http或https | 可选 | 默认值为http |
| module-relation | 表示缩容（或扩容）虚机模块和需要阻塞（或恢复）模块编号的关系，比例2:11，3表示vSRMP所在续集缩容，阻塞Cscf和Sif模块，多组之间用分号隔开 | 可选 | 2:11,3;20:100 |
| scale-in-delay-time | 缩容任务延时时间，单位秒 | 可选 | 默认值为80 |
| indicator-relation | 虚层指标名与性能统计对象，统计项的对应关系，格式为虚层指标名（vdu_指标）-性能统计对象/统计项，多组之间用分号隔开 | 可选 | slf_cpu-SystemLoad/EQPT.CPUMeanMeLoad;sif_memory-SystemLoad/EQPT.MemoryMeanMeLoad |
| file-location | 初始化生成的HostMapIp.txt、floatip.ini存放路径 | 可选 | /utxt/soft/oms/vnf |
| ftp-username | sftp 账号是oms本端sftp的账号 | 可选 | 123456 |
| ftp-password | sftp 密码 | 可选 | 123456 |
| ftp-port | sftp 端口 | 可选 | 22 |
| cf-file-location | 各虚机存放下载下来的实例化文件的地方，所有cf下的路径都是这个 | 可选 | /utxt/soft/omu/vnf |
| healing.check-off | 发起自愈开关 | 可选 | 默认为false |
| healing.check-num | 模块断开后尝试重连次数，超过该值发起虚机自愈 | 可选 | 默认值为3 |

### 2.22 prometheus：prometheus配置

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| host | 访问路径 | 必选 | |
| user | 用户名 | 可选 | 默认值为空，即不需要登陆 |
| password | 密码 | 可选 | 默认值为空，即不需要登陆 |

### 2.23 forest：forest配置

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| logEnabled | 打开或关闭日志 | 可选 | 默认为true |
| log-request | 打开/关闭Forest请求日志 | 可选 | 默认为true |
| log-response-status | 打开/关闭Forest响应状态日志 | 可选 | 默认为true |
| log-response-content | 打开/关闭Forest响应内容日志 | 可选 | 默认为false |

### 2.24 cf：cf配置

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| port | cf端口 | 必选 | |
| fileimeOut | 文件操作超时时间，单位毫秒 | 可选 | 默认值为30000 |

### 2.25 signaling：信令配置

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| signaling-queue-capacity | 信令消息队列容量 | 可选 | 默认值为10001 |
| so-file-path | 信令解析.so库文件路径 | 可选 | 默认路径为classes路径下的signalingtracking |
| track-module | 条件中没有指定模块时需要跟踪的模块 | 可选 | 列表，默认值为 ESIP, SLF, VSS |

### 2.26 alarm：告警配置

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| location-info-template | 告警配置模板 | 可选 | 默认值为vmName&ip&mac&id |
| isUnicom | 是否启用联通告警 | 可选 | 默认值为false |
| alarm-topic | Kafka告警topic | 可选 | 默认值为alarmTopic |
| alarm-id-prefix | 自定义告警ID前缀 | 可选 | 默认OMS- |
| http.api-url | API接口URL | 可选 | 默认值为null |
| http.secret-key | 密钥 | 可选 | 默认值为null |
| http.city | 资产排单区域编码 | 可选 | 默认值为null |
| http.system-code | 第三方告警权限码 | 可选 | 默认值为null |
| http.system-name | 业务系统名称 | 可选 | 默认值为null |
| http.alarm-code | 告警码列表 | 可选 | 默认值为null |
| http.person-mobile | 联系人手机号(半角逗号分隔) | 可选 | 默认值为null |
| http.person-name | 联系人姓名(半角逗号分隔) | 可选 | 默认值为null |
| http.res-type | 资产类型 | 可选 | 默认值为null |
| http.root-flag | 告警标识 | 可选 | 默认值为null |
| http.interfaceId | 发送方设备标识 | 可选 | 默认值为null |

### 2.27 datagather：性能数据采集配置（不需要可不配置，主要是为了四川运维平台开发的）

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| performancedatagatherTopic | 采集到kafka的topic名称 | 可选 | 默认值为performancedatagatherTopic |
| switch | on：打开  off：关闭 | 可选 | 默认值为off |

### 2.28 module-back：模块备份配置

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| enable | 是否启用 | 可选 | 默认为false，配置false其他配置不需要配置 |
| sftp-host | Sftp主机地址 | 可选 | |
| sftp-port | Sftp端口 | 可选 | |
| sftp-user | Sftp用户名 | 可选 | |
| sftp-password | Sftp密码 | 可选 | |
| sftp-directory | Sftp传输目录 | 可选 | |
| pack-target-directory | 本机备份目录 | 可选 | |
| pack-source-directory | 模块备份目录（每个模块单独配置，因为每个模块不一样，同一个模块有多个路径或文件以\|符号分割，{ins}转换为具体的实例名，比如sif-5 转换为sif5,为模块名+mid的形式） | 可选 | |
| --slf | 模块目录 | 可选 | |

### 2.29 resource：资源文件位置配置

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| resourcefilepath | 资源文件位置 | 可选 | 默认与application-dev.yml同级路径下 |

### 2.30 nacos：nacos配置

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| nacosOff | Nacos连接是否打开（true:false） | 可选 | 默认false |
| ip | Nacos ip | 可选 | 若nacosOff为true则必选 |
| port | Nacos端口 | 可选 | 若nacosOff为true则必选 |
| namespaceId | 命名空间 | 可选 | 若nacosOff为true则必选 |
| clusterName | 集群名称 | 可选 | 若nacosOff为true则必选 |
| username | nacos用户名 | 可选 | 若nacosOff为true则必选 |
| password | nacos密码 | 可选 | 若nacosOff为true则必选 |

### 2.31 moduleconfig：可查询配置的模块

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| modulelist | 可查询配置的模块列表 | 可选 | SIF,VSCU,ESIP,MSIP |

### 2.32 uploadtoftp性能文件上传配置（不需要可不配置）

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| username | 用户名 | 可选 | 默认值为root |
| password | 密码 | 可选 | 默认值为eastcom |

### 2.33 thresholdalarm：性能阈值告警

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| enable | 是否启动 | 可选 | 默认为false |
| timeInterval | 扫描间隔 | 可选 | 默认值为30 |
| functionName | Function名称 | 可选 | 默认值为CneFunction |
| performanceName | 指标名称 | 可选 | 默认值为CNE.StdCapsMean |
| alarmValue | 告警阈值 | 可选 | 默认值为20 |
| recoverAlarmValue | 恢复阈值 | 可选 | 默认值为10 |
| alarmContent | 告警内容 | 可选 | 默认值为CNE.StdCapsMean超过阈值 |
| alarmLevel | 告警等级 | 可选 | 默认值为3 |
| type | 告警类型（GT表示大于告警阈值，LT表示小于告警阈值） | 可选 | 默认值为GT |

### 2.34 redis：redis配置

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| enable | 是否连接redis | 可选 | 默认false，后续配置都不需要配置 |
| useCluster | 是否使用集群模式，true表示集群，false表示哨兵 | 可选 | 默认值为false |
| pool | redis连接池配置 | 可选 | |
| pool.maxTotal | redis池的最大连接数 | 可选 | 默认值为100 |
| pool.maxIdle | redis池的最大空闲数量 | 可选 | 默认值为10 |
| pool.timeout | redis池发送redis命令后的超时时间，单位为毫秒 | 可选 | 默认值为500 |
| pool.connectTimeout | redis池中连接超时时间，单位为毫秒 | 可选 | 默认值为1500 |
| pool.maxAttempts | 遇到异常时的重试次数 | 可选 | 默认值为5 |
| pool.idleConnectionTimeout | 判断连接空闲时间，单位毫秒 | 可选 | 默认值为30000 |
| db | redis数据表选择 | 可选 | |
| hosts | redis的ip | 可选 | |
| mastername | redis哨兵的主节点名 | 可选 | |
| password | redis密码 | 可选 | |

### 2.35 nginx-weight-control：nginx权重配置（omc软件升级自动化流程用到）

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| nginx-config-file-path | Nginx配置文件路径 | 可选 | 默认 /utxt/soft/nginx/conf/nginx.conf |
| hostname | Nginx所在虚机名称 | 可选 | |

### 2.36 instruction：容灾校验密码

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| password | 容灾校验密码，md5加密后值 | 可选 | 默认密码为eastcom |
| scriptInstruction | 是否使用脚本方式容灾 | 可选 | 默认false |
| instructionScriptPath | 容灾脚本instruction.sh路径 | 脚本方式容灾必选 | |
| restoringInstructionScriptPath | 容灾恢复脚本restore.sh路径 | 脚本方式容灾必选 | |
| queryScriptPath | 容灾查询脚本query.sh路径 | 脚本方式容灾必选 | |

### 2.37 connectionquerymodule：网元连接查询

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| connectionquerylist | 可查询模块（每个网元不一样，需根据实际情况配置，接口详情请参考《ES和EC间omm接口文档_V1.0.0.0.docx》10.2.2） | 可选 | KSRS,SELB,ESIP,VSCU,DMGW,MELB,VMSIP |

### 2.38 exceptionpolicyreconciliation：高频专用配置（其他网元可忽略），异常策略数据对账

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| datacheck.userdatacheck.port | 用户数据对账slf端口 | 必选 | |
| datacheck.userdatacheck.path | 用户数据对账slf地址 | 必选 | |
| datacheck.sysdatacheck.port | 系统数据对账slf端口 | 必选 | |
| datacheck.sysdatacheck.path | 系统数据对账slf地址 | 必选 | |

### 2.39 business：业务日志相关配置

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| businessTopic | 监控kafka接收business业务日志的topic | 可选 | 默认值为businessTopic |

### 2.40 pmperiodmonitor：指标颗粒度阈值监控

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| swt | 指标颗粒度阈值监控开关 | 可选 | 默认值为off |
| timePeriod | 时间颗粒度 | 可选 | 默认值为1 |
| span | 颗粒度跨度 | 可选 | 默认值为1 |
| pm.pmName | 指标名 | 可选 | 默认值为HNDS.CallNotify |
| pm.type | 比较符号 | 可选 | 默认值为GT |
| pm.alarmValue | 告警阈值 | 可选 | 默认值为20 |
| pm.recoverAlarmValue | 恢复告警阈值 | 可选 | 默认值为10 |
| pm.alarmCode | 告警码 | 可选 | 默认值为100 |
| pm.alarmContent | 告警内容 | 可选 | 默认值为指标A超过20 |

### 2.41 auth：网元直连鉴权配置

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| tokenExpireDate | token过期时间，单位秒 | 可选 | 默认值为1800 |

### 2.42 generalconfig：通用配置功能

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| localPath | 通用配置路径 | 可选 | /utxt/soft/oms/generalConfig/ |

### 2.43 interception：垃圾短信拦截配置功能

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| url | 垃圾短信拦截话单统计前端url | 可选 | 默认值为http://localhost:8082 |

---

## 3 script配置文件

### 3.1 Scriptconfig 脚本配置

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| scriptDircotry | 脚本所在目录 | 必选 | 改为实际路径 |
| scriptInfoMap.xxxx | 各脚本所对应的脚本名称 | 必选 | 无需改动，原值即可 |

---

## 4 VNFConfig配置文件（只有网元新建的时候需要配置）

### 4.1 vnfconfig网元虚机配置

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| vnfinstanceid | VNF实例标识 | 必选 | |
| vnfinstancename | VNF实例名称 | 必选 | |
| vnftype | VNF类型 | 必选 | |
| vm.vduid | 虚机所属VDU标识 | 必选 | |
| vm.vmid | 虚机UUID | 必选 | |
| vm.vmname | 虚机名称 | 必选 | |
| vm.nicdata.name | 网口名 | 必选 | |
| vm.nicdata.order | 网口顺序 | 必选 | |
| vm.nicdata.ipv4 | 网口IPv4地址 | 可选 | v4与v6至少填一个 |
| vm.nicdata.ipv6 | 网口IPv6地址 | 可选 | v4与v6至少填一个 |
| vm.nicdata.mac | 网口MAC地址 | 可选 | |

---

## 5 application-lsfixed配置文件

### 5.1 licensefixed：license固定配置部分

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| mml-map | mml命令 | 否 | |
| mml-map.xxxx | 模块名 | 否 | VSS |
| mml-map.VSS | nacos中vss的dataid和group、sftp连接信息、vss的license需要通过这条prometheus来查询 | 否 | `{"dataid":"com.eastcom.vvs","group":"DEFAULT_GROUP","sftpusername":"zyy","sftppassword":"123456","sftpport":"22","prometheuscalculation":"max(max_over_time(Max{MaxCalling=\"NCPHB01\"}[30s]))/60"}` |
| license-template | license文件模板 | 否 | |
| license-template.xxxx-license | 模块的license文件模板，其中slf里的sftpip是slf的license调度时要用的sftp的ip信息，需要单独填写，因为传参不对 | 否 | vss-license:`{"license":100}` |

**mml命令示例：**

```yaml
SLF:
  uninstall: "licenseUninstall"
  install: "licenseActive"
SIF:
  install: ":RELOAD-VSBS-CONFIG;"
VSS:
  dataid: "com.eastcom.vvs"
  group: "DEFAULT_GROUP"
  sftpusername: "zyy"
  sftppassword: "123456"
  sftpport: "22"
  prometheuscalculation: "max(max_over_time(Max{MaxCalling=\"NCPHB01\"}[30s]))/60"
```

**License文件模板示例：**

```yaml
slf-license:
  # 统计中十秒SetupMessageTimers的增长量大于license，上报license告警
  string: '{"state":"VALID","type":"sip-call","value":"100","expireDate":"2020-03-05 00:00:00"}'
  license: 100
  recover: 500
  sftpip: 10.8.29.88
```

---

## 6 application-performancefixed：性能统计固定配置

### 6.1 performancefixed：统计固定配置

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| netype-performance-map | 各个网元对应的统计功能标志 | 是 | |
| netype-performance-map.xxx | 网元名 | 是 | |

**示例：**

```yaml
CNE:
  VSCU: "4,5"
  ESCP: "0"
  ESIP: "9"
  SIF: "0"
  SLF: "0"
```

---

## 7 performanceBlock：性能容灾配置

| 配置项 | 配置项类型 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|---|
| enable | boolean | 是否启用 | 否 | false |
| timeIntervalBlock | Integer | 统计间隔 | 否 | 150 |
| moduleType | String | 需要容灾的模块 | 否 | ELB |
| historical | Integer | 历史天数(计算几天的历史平均值进行比较) | 否 | 2 |
| block.switch | Boolean | 是否容灾 | 否 | false |
| block.count | Integer | 判断容灾的总数次 | 否 | 3 |
| Block.alarmCount | Integer | 触发容灾的次数（count次内 alarmcount次触发才会进行容灾） | 否 | 2 |
| threshold.metric | String | 入门指标（只有这个指标超过阈值才会进行容灾阈值判断） | 否 | PSP.TransreInvite200 |
| threshold.value | double | 入门阈值 | 否 | 30 |
| alarmThreshold | List | 容灾指标列表 | 否 | |
| alarmThreshold.metric | String | 容灾指标名 | 否 | PSP.TransReInvite200 |
| alarmThreshold.alarmValue | double | 容灾指标告警值 | 否 | 30 |
| alarmThreshold.recoverAlarmValue | double | 容灾指标恢复值 | 否 | 10 |

---

## 8 application-inspection：作业指令配置

| 配置项 | 配置项值类型 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|---|
| dstversion | String | 软件版本号 | 否 | V3.0 |
| ntp-state-command | String | NTP同步状态检查命令 | 否 | timedatectl status |
| timezone-state | String | 检查时区命令 | 否 | timedatectl |
| pm.imeInterval | Integer | 时间间隔 | 否 | 30 |
| pm.telPerformanceStatistics.functionName | String | 统计对象 | 否 | CneFunction |
| pm.telPerformanceStatistics.performanceName | String | 指标名 | 否 | CNE.StdCapsMean |
| pm.telPerformanceStatistics.threshold | Integer | 阈值 | 否 | 20 |
| nodeExporterLabel | String | 虚机node_exporter相关指标Label{exporter}，用作查询过滤 | 否 | node |
| blackboxExporterLabel | String | 虚机black_exporter相关指标Label{exporter}，用作查询过滤 | 否 | blackbox |
| promQueryHost | String | prometheus http 查询host | 否 | http://10.8.28.185:9090 |
| cpuUsageThreshold | String | cpu占用率阈值，单位百分比，超过判定为异常 | 否 | 10.12 |
| diskUsageThreshold | String | 磁盘占用率阈值，单位百分比，超过判定为异常 | 否 | 20.22 |
| memUsageThreshold | String | 内存占用率阈值，单位百分比，超过判定为异常 | 否 | 30.22 |

---

## 9 application-dataStorage:通用数据操作配置

### 9.1 Storage 通用数据存储

| 配置项 | 配置项含义 | 是否必填 | 默认值 |
|---|---|---|---|
| key-strategy-map.配置键名称 | 存储方式 | 否 | `{"AAA-KEY":"redisStorageStrategy"}` |
