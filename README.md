## 20171231 23:23
## 2017年的最后一天，大家都在跨年，倒计时。我在苦逼的写着代码。但我自己觉得，这也许是最有意义的一次跨年了。
## Spring + SpringMVC+ Mybatis + Mysql 实现的秒杀系统。基于git上的一个系统去学习。
项目详细信息gitURL:
![](https://github.com/aomiga/seckill)
好了，现在开始我自己的。
## 需求说明：
    1.向用户展示秒杀列表，用户可根据选择满足秒杀条件的商品进行秒杀；
    2.满足秒杀条件为，商品开始在秒杀期间内，实现为暴露秒杀接口；
    3.秒杀成功的后减库存，纪录秒杀信息。
## 数据库表设计(详细内容见schema.sql)
    1. seckill 秒杀商品列表。用于纪录商品库存和ID，商品开始秒杀时间和秒杀结束时间。
    2. success_killed 秒杀成功表。 用于纪录商品ID,和用户信息。
## DAO层设计开发
### entity创建
1.创建实体类 com.ssm.entity包下 Seckill.java
2.创建实体类 com.ssm.entity包下 SuccessKilled.java
### dao接口创建
1.创建接口 com.ssm.dao.SecKillDao.java
2.创建接口 com.ssm.dao.SuccessKilledDao.java
### Mybatis 配置。
接下来基于MyBatis来实现我们之前设计的Dao层接口。
首先需要配置我们的MyBatis，在resources包下创建MyBatis全局配置文件mybatis-config.xml文件，
在浏览器中输入`http://mybatis.github.io/mybatis-3/zh/index.html`打开MyBatis的官网文档，点击左边的"入门"栏框，找到mybatis全局配置文件，在这里有xml的一个规范，也就是它的一个xml约束，拷贝:
```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE configuration
  PUBLIC "-//mybatis.org//DTD Config 3.0//EN"
  "http://mybatis.org/dtd/mybatis-3-config.dtd">
```

到我们的项目mybatis全局配置文件中，然后在全局配置文件中加入如下配置信息:
```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE configuration
        PUBLIC "-//mybatis.org//DTD Config 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-config.dtd">
<configuration>
    <!--配置全局属性-->
    <settings>
        <!--使用jdbc的getGeneratekeys获取自增主键值-->
        <setting name="useGeneratedKeys" value="true"/>
        <!--使用列别名替换列名　　默认值为true
        select name as title(实体中的属性名是title) form table;
        开启后mybatis会自动帮我们把表中name的值赋到对应实体的title属性中
        -->
        <setting name="useColumnLabel" value="true"/>

        <!--开启驼峰命名转换Table:create_time到 Entity(createTime)-->
        <setting name="mapUnderscoreToCamelCase" value="true"/>
    </settings>

</configuration>
```

配置文件创建好后我们需要关注的是Dao接口该如何实现，mybatis为我们提供了mapper动态代理开发的方式为我们自动实现Dao的接口。在mapper包下创建对应Dao接口的xml映射文件，里面用于编写我们操作数据库的sql语句，SeckillDao.xml和SuccessKilledDao.xml。既然又是一个xml文件，我们肯定需要它的dtd文件，在官方文档中，点击左侧"XML配置"，在它的一些事例中，找到它的xml约束:
```xml
<!DOCTYPE mapper
    PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
    "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
```

加入到两个mapper映射xml文件中，然后对照Dao层方法编写我们的映射文件内容如下:  

SeckillDao.xml:
```xml
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="cn.codingxiaxw.dao.SeckillDao">
    <!--目的:为dao接口方法提供sql语句配置
    即针对dao接口中的方法编写我们的sql语句-->


    <update id="reduceNumber">
        UPDATE seckill
        SET number = number-1
        WHERE seckill_id=#{seckillId}
        AND start_time <![CDATA[ <= ]]> #{killTime}
        AND end_time >= #{killTime}
        AND number > 0;
    </update>

    <select id="queryById" resultType="Seckill" parameterType="long">
        SELECT *
        FROM seckill
        WHERE seckill_id=#{seckillId}
    </select>

    <select id="queryAll" resultType="Seckill">
        SELECT *
        FROM seckill
        ORDER BY create_time DESC
        limit #{offset},#{limit}
    </select>


</mapper>
```

SuccessKilledDao.xml:
```xml
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="cn.codingxiaxw.dao.SuccessKilledDao">

    <insert id="insertSuccessKilled">
        <!--当出现主键冲突时(即重复秒杀时)，会报错;不想让程序报错，加入ignore-->
        INSERT ignore INTO success_killed(seckill_id,user_phone,state)
        VALUES (#{seckillId},#{userPhone},0)
    </insert>

    <select id="queryByIdWithSeckill" resultType="SuccessKilled">

        <!--根据seckillId查询SuccessKilled对象，并携带Seckill对象-->
        <!--如何告诉mybatis把结果映射到SuccessKill属性同时映射到Seckill属性-->
        <!--可以自由控制SQL语句-->
        SELECT
            sk.seckill_id,
            sk.user_phone,
            sk.create_time,
            sk.state,
            s.seckill_id "seckill.seckill_id",
            s.name "seckill.name",
            s.number "seckill",
            s.start_time "seckill.start_time",
            s.end_time "seckill.end_time",
            s.create_time "seckill.create_time"
        FROM success_killed sk
        INNER JOIN seckill s ON sk.seckill_id=s.seckill_id
        WHERE sk.seckill_id=#{seckillId}
        AND sk.user_phone=#{userPhone}
    </select>

</mapper>
```
### Mybatis 与Spring 整合。－－
接下来我们开始MyBatis和Spring的整合，整合目标:1.更少的编码:只写接口，不写实现类。2.更少的配置:别名、配置扫描映射xml文件、dao实现。3.足够的灵活性:自由定制SQL语句、自由传结果集自动赋值。  

在resources包下创建一个spring包，里面放置spring对Dao、Service、transaction的配置文件。在浏览器中输入`http://docs.spring.io/spring/docs/`进入到Spring的官网中下载其pdf官方文档，在其官方文档中找到它的xml的定义内容头部:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:context="http://www.springframework.org/schema/context"
       xsi:schemaLocation="http://www.springframework.org/schema/beans
        http://www.springframework.org/schema/beans/spring-beans.xsd http://www.springframework.org/schema/context http://www.springframework.org/schema/context/spring-context.xsd">
```

在spring包下创建一个spring配置dao层对象的配置文件spring-dao.xml，加入上述dtd约束，然后添加二者整合的配置，内容如下:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:context="http://www.springframework.org/schema/context"
       xsi:schemaLocation="http://www.springframework.org/schema/beans
        http://www.springframework.org/schema/beans/spring-beans.xsd
        http://www.springframework.org/schema/context http://www.springframework.org/schema/context/spring-context.xsd">

    <!--配置整合mybatis过程
    1.配置数据库相关参数-->
    <context:property-placeholder location="classpath:jdbc.properties"/>

    <!--2.数据库连接池-->
    <bean id="dataSource" class="com.mchange.v2.c3p0.ComboPooledDataSource">
        <!--配置连接池属性-->
        <property name="driverClass" value="${driver}" />

        <!-- 基本属性 url、user、password -->
        <property name="jdbcUrl" value="${url}" />
        <property name="user" value="${username}" />
        <property name="password" value="${password}" />

        <!--c3p0私有属性-->
        <property name="maxPoolSize" value="30"/>
        <property name="minPoolSize" value="10"/>
        <!--关闭连接后不自动commit-->
        <property name="autoCommitOnClose" value="false"/>

        <!--获取连接超时时间-->
        <property name="checkoutTimeout" value="1000"/>
        <!--当获取连接失败重试次数-->
        <property name="acquireRetryAttempts" value="2"/>
    </bean>

    <!--约定大于配置-->
    <!--３.配置SqlSessionFactory对象-->
    <bean id="sqlSessionFactory" class="org.mybatis.spring.SqlSessionFactoryBean">
        <!--往下才是mybatis和spring真正整合的配置-->
        <!--注入数据库连接池-->
        <property name="dataSource" ref="dataSource"/>
        <!--配置mybatis全局配置文件:mybatis-config.xml-->
        <property name="configLocation" value="classpath:mybatis-config.xml"/>
        <!--扫描entity包,使用别名,多个用;隔开-->
        <property name="typeAliasesPackage" value="cn.codingxiaxw.entity"/>
        <!--扫描sql配置文件:mapper需要的xml文件-->
        <property name="mapperLocations" value="classpath:mapper/*.xml"/>
    </bean>

    <!--４:配置扫描Dao接口包,动态实现DAO接口,注入到spring容器-->
    <bean class="org.mybatis.spring.mapper.MapperScannerConfigurer">
        <!--注入SqlSessionFactory-->
        <property name="sqlSessionFactoryBeanName" value="sqlSessionFactory"/>
        <!-- 给出需要扫描的Dao接口-->
        <property name="basePackage" value="cn.codingxiaxw.dao"/>
    </bean>
</beans>
```







