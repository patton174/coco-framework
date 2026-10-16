package io.github.coco.feature.mybatisplus.datasource;

import io.github.coco.feature.mybatisplus.CocoMybatisPlusErrorCode;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/**
 * 处理 {@link CocoDataSource} 声明式数据源切换的 AOP 切面。
 * <p>
 * 支持类级、方法级、接口级以及 MyBatis Mapper 动态代理接口上的 {@link CocoDataSource} 声明，
 * 在进入方法前压栈目标数据源键并在退出时自动恢复上一层路由状态，同时提供活动本地事务内跨源切换保护。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-mybatis-plus}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoDataSourceAspect implements MethodInterceptor, Ordered {

    private static final Logger LOGGER = LoggerFactory.getLogger(CocoDataSourceAspect.class);

    private final CocoDataSourceKeyResolver keyResolver;

    private final CocoMybatisPlusDataSourceProperties properties;

    private final ConcurrentMap<OperationKey, DataSourceOperation> operations = new ConcurrentHashMap<>();

    /**
     * <p>
     * 创建数据源切换切面。
     * </p>
     * @param keyResolver 路由键解析器
     * @param properties 多数据源配置属性
     */
    public CocoDataSourceAspect(
            CocoDataSourceKeyResolver keyResolver,
            CocoMybatisPlusDataSourceProperties properties) {
        this.keyResolver = Objects.requireNonNull(keyResolver, "keyResolver must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Method method = invocation.getMethod();
        if (method.getDeclaringClass() == Object.class) {
            return invocation.proceed();
        }
        Object target = invocation.getThis();
        Class<?> targetClass = target != null ? target.getClass() : method.getDeclaringClass();
        DataSourceOperation operation = this.operations.computeIfAbsent(
                new OperationKey(method, targetClass), key -> resolveOperation(key.method(), key.targetClass()));
        if (operation.dataSource() == null) {
            return invocation.proceed();
        }
        CocoDataSource annotation = operation.dataSource();
        String expression = StringUtils.hasText(annotation.key()) ? annotation.key() : annotation.value();
        String resolvedKey = this.keyResolver.resolve(
                expression,
                target,
                operation.specificMethod(),
                invocation.getArguments());
        checkTransactionConflict(resolvedKey, operation.specificMethod());
        try (CocoDataSourceScope ignored = CocoDataSourceContextHolder.push(resolvedKey)) {
            return invocation.proceed();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getOrder() {
        return this.properties.getAspectOrder();
    }

    static boolean hasDataSource(Method method, Class<?> targetClass) {
        if (method == null || method.getDeclaringClass() == Object.class) {
            return false;
        }
        return resolveOperation(method, targetClass).dataSource() != null;
    }

    private void checkTransactionConflict(String targetKey, Method method) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            return;
        }
        String activeKey = CocoDataSourceContextHolder.current().orElse(this.properties.getPrimary());
        if (Objects.equals(activeKey, targetKey)) {
            return;
        }
        CocoDataSourceTransactionPolicy policy = this.properties.getTransactionPolicy();
        if (policy == CocoDataSourceTransactionPolicy.ALLOW) {
            return;
        }
        if (policy == CocoDataSourceTransactionPolicy.WARN) {
            LOGGER.warn(
                    "Coco MyBatis-Plus detected datasource switch from '{}' to '{}' inside an active transaction on method '{}'.",
                    activeKey,
                    targetKey,
                    method.toGenericString());
            return;
        }
        throw CocoMybatisPlusErrorCode.DATASOURCE_TRANSACTION_CONFLICT.conflict();
    }

    private static DataSourceOperation resolveOperation(Method signatureMethod, Class<?> targetClass) {
        Class<?> effectiveClass = targetClass != null ? targetClass : signatureMethod.getDeclaringClass();
        Method specificMethod = resolveSpecificMethod(signatureMethod, effectiveClass);
        Set<Method> candidateMethods = new LinkedHashSet<>();
        candidateMethods.add(specificMethod);
        candidateMethods.add(signatureMethod);
        addBridgeMethods(effectiveClass, specificMethod, candidateMethods);
        addInterfaceMethods(effectiveClass, signatureMethod, candidateMethods);
        if (signatureMethod.getDeclaringClass().isInterface()) {
            addInterfaceMethods(signatureMethod.getDeclaringClass(), signatureMethod, candidateMethods);
        }
        for (Method candidate : candidateMethods) {
            CocoDataSource dataSource = AnnotatedElementUtils.findMergedAnnotation(candidate, CocoDataSource.class);
            if (dataSource != null) {
                return new DataSourceOperation(dataSource, candidate);
            }
        }
        for (Class<?> candidateType : resolveCandidateTypes(effectiveClass, signatureMethod.getDeclaringClass())) {
            CocoDataSource dataSource = AnnotatedElementUtils.findMergedAnnotation(candidateType, CocoDataSource.class);
            if (dataSource != null) {
                return new DataSourceOperation(dataSource, specificMethod);
            }
        }
        return new DataSourceOperation(null, specificMethod);
    }

    private static Method resolveSpecificMethod(Method signatureMethod, Class<?> targetClass) {
        if (targetClass == null || Proxy.isProxyClass(targetClass)) {
            return signatureMethod;
        }
        return AopUtils.getMostSpecificMethod(signatureMethod, targetClass);
    }

    private static void addBridgeMethods(Class<?> targetClass, Method specificMethod, Set<Method> methods) {
        if (targetClass == null) {
            return;
        }
        methods.add(BridgeMethodResolver.findBridgedMethod(specificMethod));
        for (Method candidate : targetClass.getMethods()) {
            if (!candidate.isBridge()) {
                continue;
            }
            Method bridged = BridgeMethodResolver.findBridgedMethod(candidate);
            if (bridged.equals(specificMethod)) {
                methods.add(candidate);
                methods.add(bridged);
            }
        }
    }

    private static void addInterfaceMethods(Class<?> type, Method signatureMethod, Set<Method> methods) {
        if (type == null) {
            return;
        }
        if (type.isInterface()) {
            for (Method candidate : type.getMethods()) {
                if (matches(signatureMethod, candidate)) {
                    methods.add(candidate);
                }
            }
        }
        for (Class<?> interfaceType : type.getInterfaces()) {
            for (Method candidate : interfaceType.getMethods()) {
                if (matches(signatureMethod, candidate)) {
                    methods.add(candidate);
                }
            }
            addInterfaceMethods(interfaceType, signatureMethod, methods);
        }
        Class<?> superclass = type.getSuperclass();
        if (superclass != null && superclass != Object.class) {
            addInterfaceMethods(superclass, signatureMethod, methods);
        }
    }

    private static boolean matches(Method expected, Method candidate) {
        if (!expected.getName().equals(candidate.getName())
                || expected.getParameterCount() != candidate.getParameterCount()) {
            return false;
        }
        Class<?>[] expectedParameters = expected.getParameterTypes();
        Class<?>[] candidateParameters = candidate.getParameterTypes();
        for (int index = 0; index < expectedParameters.length; index++) {
            if (!expectedParameters[index].isAssignableFrom(candidateParameters[index])
                    && !candidateParameters[index].isAssignableFrom(expectedParameters[index])) {
                return false;
            }
        }
        return true;
    }

    private static List<Class<?>> resolveCandidateTypes(Class<?> targetClass, Class<?> declaringClass) {
        List<Class<?>> result = new ArrayList<>();
        collectTypes(targetClass, result);
        if (declaringClass != null && declaringClass != targetClass) {
            collectTypes(declaringClass, result);
        }
        return result;
    }

    private static void collectTypes(Class<?> type, List<Class<?>> result) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            if (! Proxy.isProxyClass(current) && !result.contains(current)) {
                result.add(current);
            }
        }
        addInterfaceTypes(type, result);
    }

    private static void addInterfaceTypes(Class<?> type, List<Class<?>> types) {
        if (type == null) {
            return;
        }
        for (Class<?> interfaceType : type.getInterfaces()) {
            if (!types.contains(interfaceType)) {
                types.add(interfaceType);
                addInterfaceTypes(interfaceType, types);
            }
        }
        Class<?> superclass = type.getSuperclass();
        if (superclass != null && superclass != Object.class) {
            addInterfaceTypes(superclass, types);
        }
    }

    private record DataSourceOperation(CocoDataSource dataSource, Method specificMethod) {
    }

    private record OperationKey(Method method, Class<?> targetClass) {
    }
}
