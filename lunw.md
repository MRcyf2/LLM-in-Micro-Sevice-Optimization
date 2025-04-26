
@DyThreadPool 注解

用途：标记线程池创建方法，提供动态调整所需的元数据
关键参数：
poolName（强制）: 线程池唯一标识符
minCore/maxCore: 核心线程数的动态调整边界（当前已实现调优）
maxQueueSize: 队列容量上限（默认200，但您提到尚未实现队列调优）
设计特征：
仅允许标注在方法上（@Target(ElementType.METHOD)）
运行时保留策略（@Retention(RetentionPolicy.RUNTIME)）

ThreadPoolConfig 类
核心作用：声明Spring管理的线程池Bean，通过@DyThreadPool注解定义各线程池的动态调整参数
关键实现细节：
orderThreadPool使用ResizableLinkedBlockingQueue（可动态调整容量队列），而paymentServicePool使用标准LinkedBlockingQueue
构造参数与注解参数存在差异（如orderThreadPool构造时corePoolSize=8，但注解的minCore=4），可能意味着动态调整会覆盖初始值

DyThreadPoolBeanProcessor 类
核心作用：在Spring Bean初始化后拦截ThreadPoolExecutor实例，将带有@DyThreadPool注解的线程池注册到中央仓库
关键实现细节：
通过BeanDefinition回溯到@Bean方法源码，提取注解元数据
依赖MethodMetadata识别配置类中线程池的创建方法，存在潜在风险（如代理类场景可能无法获取原始方法）

ThreadPoolRegistry 类
核心作用：作为线程池管理中心，维护所有动态线程池的元数据，提供参数调整和状态查询能力
关键机制：
线程安全存储：使用ConcurrentHashMap存储PoolMetadata（含线程池实例及动态调整边界）
动态约束调整：adjust()方法强制将输入参数限制在注解定义的minCore/maxCore范围内（如newCore=50但maxCore=32时，实际设为32）
AI集成：adjustByAi()通过DeepseekClient获取建议值并直接应用（需确认是否应复用adjust()的约束逻辑）
监控指标计算：getAllPoolDetails()实时计算线程活跃率、队列使用率等关键指标
PoolMetadata 记录类
参数校验：构造时强制检查minCore <= maxCore（通过IllegalArgumentException阻止非法配置）
数据不可变性：记录类（Record）特性确保元数据在注册后不可篡改

ThreadPoolMetricsExporter 类
核心作用：将线程池的运行时指标（核心线程数、活跃线程、队列使用率等）通过Micrometer暴露给Prometheus，支持动态注册和定时刷新
关键机制：
延迟初始化：监听Spring上下文就绪事件后延迟500ms注册初始指标，避免竞争条件
动态检测：定时任务每30秒扫描新增线程池并自动注册指标（通过registeredPools跟踪已注册池）
防御性指标计算：队列容量获取兼容ResizableLinkedBlockingQueue和标准队列，避免除零错误
拒绝策略埋点：InstrumentedRejectionHandler在任务拒绝时记录Counter指标及详细日志
InstrumentedRejectionHandler 内部类
增强监控：继承自AbortPolicy，在任务拒绝时触发指标计数和WARN日志，通过threadpool.rejected.tasks指标可追踪各池拒绝情况

ResizableLinkedBlockingQueue 类
核心作用：实现可动态调整容量的阻塞队列，用于支持线程池队列大小的动态调优
关键机制：
容量动态修改：通过setCapacity()方法实时更新队列容量
非阻塞式约束：重写offer()方法，仅在当前元素数小于动态容量时允许入队
线程协调：容量扩大时唤醒等待的生产者线程（通过notifyAll()

ThreadPoolFileRecorder 类
核心作用：将线程池的配置参数和运行时指标按固定间隔（30秒）持久化到本地CSV文件，每个线程池独立文件
关键机制：
文件隔离：通过sanitizeFileName清洗池名称生成安全文件名（如orderServicePool→orderServicePoolDetail.txt）
表头初始化：首次写入时自动添加CSV列头，包含时间戳、配置参数和运行时指标
指标复用：复用ThreadPoolMetricsExporter的队列容量计算逻辑，保证与监控指标的一致性

DeepseekClient 类
核心作用：通过读取线程池的监控日志文件，调用Deepseek API获取线程池参数优化建议（核心/最大线程数）
关键流程：
日志解析：读取ThreadPoolFileRecorder生成的CSV文件，转换为结构化数据
提示工程：构造包含配置约束和监控数据的系统提示（System Prompt），强制要求返回JSON格式
API调用：发送HTTP请求至Deepseek接口，解析返回的JSON建议

ThreadPoolController 类
核心作用：提供REST API用于动态线程池的监控、参数调整及压力测试，支持手动/AI调参及负载模拟
关键端点：
/status：获取所有线程池的配置与实时状态
/adjust：手动调整指定线程池的核心/最大线程数
/adjust-ai：触发AI驱动的参数自动优化
/stress-test：提交批量任务模拟压力（同步阻塞）
/stress-test2：速率控制的异步压力测试（支持QPS限制）