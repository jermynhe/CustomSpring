package com.james;

import com.james.annotation.JAutowired;
import com.james.annotation.JController;
import com.james.annotation.JRequestMapping;
import com.james.annotation.JService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

// 创建JDispatchServlet类，重写init()、doGet()和doPost()方法。
public class JDispatchServlet extends HttpServlet {
    // 保存所有被扫描到的相关类 com.james.demo.DemoController，com.fang.demo.DemoServiceImpl
    public List<String> clazzNames = new ArrayList<>();
    // 保存所有初始化的Bean demoController new DemoController()
    public Map<String, Object> iocMap = new HashMap<>();
    // 保存所有的url和Method的映射关系 /say Method
    public Map<String, Method> handlerMap = new HashMap<>();
    // 和web.xml里init-param的值一致
    private static final String LOCATION = "contextConfigLocation";
    // 保存配置的所有信息
    private Properties p = new Properties();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doDispatch(req, resp);
        } catch (Exception e) {
            resp.getWriter().write("500 Exception: " + Arrays.toString(e.getStackTrace()));
        }
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        // 1、加载配置文件
        doLoadConfig(config.getInitParameter(LOCATION));
        // 2、扫描所有相关的类
        doScanner(p.getProperty("scanPackage"));
        // 3、初始化所有相关类的实例，并保存到IOC容器中
        doInstance();
        // 4、依赖注入
        doAutowired();
        // 5、构造HandlerMapping
        initHandlerMapping();
        // 6、等待请求，匹配URL，定位方法，反射
        System.out.println("Jun Spring is init");
    }

    private void doLoadConfig(String location) {
        InputStream is = null;
        System.out.println("location = " + location);
        is = getServletContext().getResourceAsStream(location);
        try {
            p.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (null != is) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void doScanner(String packageName) {
        // com.james.demo -> com/james/demo
        // 正则表达式：\.表示除换行符\n之外的任何单字符，\\.表示.
        URL url = this.getClass().getClassLoader().getResource("/" + packageName.replaceAll("\\.", "/"));
        File dir = new File(url.getFile());
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                doScanner(packageName + "." + file.getName());
            } else {
                clazzNames.add(packageName + "." + file.getName().replace(".class", "").trim());
            }
        }
    }

    private String lowerFirstCase(String simpleName) {
        char[] chars = simpleName.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }

    private void doInstance() {
        if (clazzNames.size() == 0) {
            return;
        }
        for (String clazzName : clazzNames) {
            try {
                Class<?> clazz = Class.forName(clazzName);
                if (clazz.isAnnotationPresent(JController.class)) {
                    String beanName = lowerFirstCase(clazz.getSimpleName());
                    iocMap.put(beanName, clazz.newInstance());
                } else if (clazz.isAnnotationPresent(JService.class)) {
                    JService service = clazz.getAnnotation(JService.class);
                    String beanName = service.value();
                    if (!"".equals(beanName.trim())) {
                        iocMap.put(beanName, clazz.newInstance());
                        continue;
                    }
                    Class<?>[] interfaces = clazz.getInterfaces();
                    for (Class<?> i : interfaces) {
                        iocMap.put(lowerFirstCase(i.getSimpleName()), clazz.newInstance());
                    }
                } else {
                    continue;
                }
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InstantiationException e) {
                e.printStackTrace();
            }
        }
    }

    private void doAutowired() {
        if (iocMap.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : iocMap.entrySet()) {
            // 获取所有的属性
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if (field.isAnnotationPresent(JAutowired.class)) {
                    JAutowired autowired = field.getAnnotation(JAutowired.class);
                    String beanName = autowired.value();
                    if ("".equals(beanName)) {
                        beanName = field.getType().getSimpleName();
                        beanName = lowerFirstCase(beanName);
                    }
                    // 设置可访问
                    field.setAccessible(true);
                    try {
                        // 给对象的属性赋值
                        field.set(entry.getValue(), iocMap.get(beanName));
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void initHandlerMapping() {
        if (iocMap.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : iocMap.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            if (!clazz.isAnnotationPresent(JController.class)) {
                continue;
            }
            StringBuilder baseUrl = new StringBuilder("/");
            if (clazz.isAnnotationPresent(JRequestMapping.class)) {
                JRequestMapping requestMapping = clazz.getAnnotation(JRequestMapping.class);
                baseUrl.append(requestMapping.value());
            }
            Method[] methods = clazz.getMethods();
            for (Method method : methods) {
                if (method.isAnnotationPresent(JRequestMapping.class)) {
                    JRequestMapping requestMapping = method.getAnnotation(JRequestMapping.class);
                    baseUrl.append("/" + requestMapping.value());
                    String url = baseUrl.toString().replaceAll("/+", "/");
                    handlerMap.put(url, method);
                    System.out.println("url:" + baseUrl + "," + method);
                }
            }
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        if (handlerMap.isEmpty()) {
            return;
        }
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replace(contextPath, "").replaceAll("/+", "/");
        if (!handlerMap.containsKey(url)) {
            resp.getWriter().write("404 Not Founded");
            return;
        }
        Map<String, String[]> params = req.getParameterMap();
        Method method = handlerMap.get(url);
        Class<?>[] parameterTypes = method.getParameterTypes();
        Object[] paramValues = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> paramType = parameterTypes[i];
            if (paramType == HttpServletRequest.class) {
                paramValues[i] = req;
            } else if (paramType == HttpServletResponse.class) {
                paramValues[i] = resp;
            } else if (paramType == String.class) {
                for (Map.Entry<String, String[]> entry : params.entrySet()) {
                    String value = Arrays.toString(entry.getValue())
                            .replaceAll("\\[|\\]", "")
                            .replaceAll("\\s", "");
                    paramValues[i] = value;
                }
            }
        }
        String beanName = lowerFirstCase(method.getDeclaringClass().getSimpleName());
        String result = (String) method.invoke(iocMap.get(beanName), paramValues);
        resp.getWriter().write(result);
    }
}
