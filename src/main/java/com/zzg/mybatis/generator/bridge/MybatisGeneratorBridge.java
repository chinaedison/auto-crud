package com.zzg.mybatis.generator.bridge;

import com.zzg.mybatis.generator.model.DatabaseConfig;
import com.zzg.mybatis.generator.model.DbType;
import com.zzg.mybatis.generator.model.GeneratorConfig;
import com.zzg.mybatis.generator.plugins.DbRemarksCommentGenerator;
import com.zzg.mybatis.generator.util.ConfigHelper;
import com.zzg.mybatis.generator.util.DbUtil;
import com.zzg.mybatis.generator.util.MyStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.mybatis.generator.api.*;
import org.mybatis.generator.api.dom.java.Field;
import org.mybatis.generator.api.dom.java.TopLevelClass;
import org.mybatis.generator.api.dom.xml.Attribute;
import org.mybatis.generator.api.dom.xml.Document;
import org.mybatis.generator.api.dom.xml.Element;
import org.mybatis.generator.api.dom.xml.XmlElement;
import org.mybatis.generator.config.*;
import org.mybatis.generator.internal.DefaultShellCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import java.io.*;
import java.util.*;

/**
 * The bridge between GUI and the mybatis generator. All the operation to  mybatis generator should proceed through this
 * class
 * <p>
 * Created by Owen on 6/30/16.
 */
public class MybatisGeneratorBridge {

	private static final Logger _LOG = LoggerFactory.getLogger(MybatisGeneratorBridge.class);

    private GeneratorConfig generatorConfig;

    private DatabaseConfig selectedDatabaseConfig;

    private ProgressCallback progressCallback;

    private List<IgnoredColumn> ignoredColumns;

    private List<ColumnOverride> columnOverrides;

    public MybatisGeneratorBridge() {
    }

    public void setGeneratorConfig(GeneratorConfig generatorConfig) {
        this.generatorConfig = generatorConfig;
    }

    public void setDatabaseConfig(DatabaseConfig databaseConfig) {
        this.selectedDatabaseConfig = databaseConfig;
    }

    public void generate() throws Exception {
        Configuration configuration = new Configuration();
        Context context = new Context(ModelType.CONDITIONAL);
        configuration.addContext(context);
        context.addProperty("javaFileEncoding", "UTF-8");
	    String connectorLibPath = ConfigHelper.findConnectorLibPath(selectedDatabaseConfig.getDbType());
	    _LOG.info("connectorLibPath: {}", connectorLibPath);
	    configuration.addClasspathEntry(connectorLibPath);
        // Table configuration
        TableConfiguration tableConfig = new TableConfiguration(context);
        tableConfig.setTableName(generatorConfig.getTableName());
        tableConfig.setDomainObjectName(generatorConfig.getDomainObjectName());
        if(!generatorConfig.isUseExampe()) {
            tableConfig.setUpdateByExampleStatementEnabled(false);
            tableConfig.setCountByExampleStatementEnabled(false);
            tableConfig.setDeleteByExampleStatementEnabled(false);
            tableConfig.setSelectByExampleStatementEnabled(false);
        }

	    if (DbType.MySQL.name().equals(selectedDatabaseConfig.getDbType())) {
		    tableConfig.setSchema(selectedDatabaseConfig.getSchema());
	    } else {
            tableConfig.setCatalog(selectedDatabaseConfig.getSchema());
	    }

        // 针对 postgresql 单独配置
        if (DbType.valueOf(selectedDatabaseConfig.getDbType()).getDriverClass() == "org.postgresql.Driver") {
            tableConfig.setDelimitIdentifiers(true);
        }

        //添加GeneratedKey主键生成
		if (StringUtils.isNoneEmpty(generatorConfig.getGenerateKeys())) {
			tableConfig.setGeneratedKey(new GeneratedKey(generatorConfig.getGenerateKeys(), selectedDatabaseConfig.getDbType(), true, null));
		}

        if (generatorConfig.getMapperName() != null) {
            tableConfig.setMapperName(generatorConfig.getMapperName());
        }
        // add ignore columns
        if (ignoredColumns != null) {
            ignoredColumns.stream().forEach(ignoredColumn -> {
                tableConfig.addIgnoredColumn(ignoredColumn);
            });
        }
        if (columnOverrides != null) {
            columnOverrides.stream().forEach(columnOverride -> {
                tableConfig.addColumnOverride(columnOverride);
            });
        }
        if (generatorConfig.isUseActualColumnNames()) {
			tableConfig.addProperty("useActualColumnNames", "true");
        }
        JDBCConnectionConfiguration jdbcConfig = new JDBCConnectionConfiguration();
        // http://www.mybatis.org/generator/usage/mysql.html
        if (DbType.MySQL.name().equals(selectedDatabaseConfig.getDbType())) {
	        jdbcConfig.addProperty("nullCatalogMeansCurrent", "true");
        }
        jdbcConfig.setDriverClass(DbType.valueOf(selectedDatabaseConfig.getDbType()).getDriverClass());
        jdbcConfig.setConnectionURL(DbUtil.getConnectionUrlWithSchema(selectedDatabaseConfig));
        jdbcConfig.setUserId(selectedDatabaseConfig.getUsername());
        jdbcConfig.setPassword(selectedDatabaseConfig.getPassword());
        // java model
        JavaModelGeneratorConfiguration modelConfig = new JavaModelGeneratorConfiguration();
        modelConfig.setTargetPackage(generatorConfig.getModelPackage());
        modelConfig.setTargetProject(generatorConfig.getProjectFolder() + "/" + generatorConfig.getModelPackageTargetFolder());
        // Mapper configuration
        SqlMapGeneratorConfiguration mapperConfig = new SqlMapGeneratorConfiguration();
        mapperConfig.setTargetPackage(generatorConfig.getMappingXMLPackage());
        mapperConfig.setTargetProject(generatorConfig.getProjectFolder() + "/" + generatorConfig.getMappingXMLTargetFolder());
        // DAO
        JavaClientGeneratorConfiguration daoConfig = new JavaClientGeneratorConfiguration();
        daoConfig.setConfigurationType("XMLMAPPER");
        daoConfig.setTargetPackage(generatorConfig.getDaoPackage());
        daoConfig.setTargetProject(generatorConfig.getProjectFolder() + "/" + generatorConfig.getDaoTargetFolder());


        context.setId("myid");
        context.addTableConfiguration(tableConfig);
        context.setJdbcConnectionConfiguration(jdbcConfig);
        context.setJdbcConnectionConfiguration(jdbcConfig);
        context.setJavaModelGeneratorConfiguration(modelConfig);
        context.setSqlMapGeneratorConfiguration(mapperConfig);
        context.setJavaClientGeneratorConfiguration(daoConfig);
        // Comment
        CommentGeneratorConfiguration commentConfig = new CommentGeneratorConfiguration();
        commentConfig.setConfigurationType(DbRemarksCommentGenerator.class.getName());
        if (generatorConfig.isComment()) {
            commentConfig.addProperty("columnRemarks", "true");
        }
        if (generatorConfig.isAnnotation()) {
            commentConfig.addProperty("annotations", "true");
        }
        context.setCommentGeneratorConfiguration(commentConfig);
        // set java file encoding
        context.addProperty(PropertyRegistry.CONTEXT_JAVA_FILE_ENCODING, generatorConfig.getEncoding());
        
        //实体添加序列化
        PluginConfiguration serializablePluginConfiguration = new PluginConfiguration();
        serializablePluginConfiguration.addProperty("type", "org.mybatis.generator.plugins.SerializablePlugin");
        serializablePluginConfiguration.setConfigurationType("org.mybatis.generator.plugins.SerializablePlugin");
        context.addPluginConfiguration(serializablePluginConfiguration);
        // toString, hashCode, equals插件
        if (generatorConfig.isNeedToStringHashcodeEquals()) {
            PluginConfiguration pluginConfiguration1 = new PluginConfiguration();
            pluginConfiguration1.addProperty("type", "org.mybatis.generator.plugins.EqualsHashCodePlugin");
            pluginConfiguration1.setConfigurationType("org.mybatis.generator.plugins.EqualsHashCodePlugin");
            context.addPluginConfiguration(pluginConfiguration1);
            PluginConfiguration pluginConfiguration2 = new PluginConfiguration();
            pluginConfiguration2.addProperty("type", "org.mybatis.generator.plugins.ToStringPlugin");
            pluginConfiguration2.setConfigurationType("org.mybatis.generator.plugins.ToStringPlugin");
            context.addPluginConfiguration(pluginConfiguration2);
        }
        // limit/offset插件
        if (generatorConfig.isOffsetLimit()) {
            if (DbType.MySQL.name().equals(selectedDatabaseConfig.getDbType())
		            || DbType.PostgreSQL.name().equals(selectedDatabaseConfig.getDbType())) {
                PluginConfiguration pluginConfiguration = new PluginConfiguration();
                pluginConfiguration.addProperty("type", "com.zzg.mybatis.generator.plugins.MySQLLimitPlugin");
                pluginConfiguration.setConfigurationType("com.zzg.mybatis.generator.plugins.MySQLLimitPlugin");
                context.addPluginConfiguration(pluginConfiguration);
            }
        }
        context.setTargetRuntime("MyBatis3");

        List<String> warnings = new ArrayList<>();
        Set<String> fullyqualifiedTables = new HashSet<>();
        Set<String> contexts = new HashSet<>();
        ShellCallback shellCallback = new DefaultShellCallback(true); // override=true
        MyBatisGenerator myBatisGenerator = new MyBatisGenerator(configuration, shellCallback, warnings);
        myBatisGenerator.generate(progressCallback, contexts, fullyqualifiedTables);

        //generate controller,js,html add by chinaedison
        List<GeneratedJavaFile> list = myBatisGenerator.getGeneratedJavaFiles();

        List<GeneratedXmlFile> xmllist = myBatisGenerator.getGeneratedXmlFiles();

        GeneratedJavaFile entityFile = list.get(0);
        String fileName = entityFile.getFileName().replace(".java", "");
        String str = "package " + modelConfig.getTargetPackage() + ";\n"
            + "public class " + fileName + "Req extends " + fileName + " {\n"
            + "    private Long[] idList;\n"
            + "    private int start;\n"
            + "    private int limit;\n"
            + "    public int getStart() {\n"
            + "        return start;\n"
            + "    }\n"
            + "    public void setStart(int start) {\n"
            + "        this.start = start;\n"
            + "    }\n"
            + "    public int getLimit() {\n"
            + "        return limit;\n"
            + "    }\n"
            + "    public void setLimit(int limit) {\n"
            + "        this.limit = limit;\n"
            + "    }\n"
            + "    public Long[] getIdList() {\n"
            + "        return idList;\n" +
                "    }\n" +
                "\n" +
                "    public void setIdList(Long[] idList) {\n" +
                "        this.idList = idList;\n" +
                "    }"
            + "}\n";

        String fileReq = fileName + "Req";
        File file = new File(modelConfig.getTargetProject() + "\\" + fileReq + ".java");
        if (null != str && !"".equals(str)) {
            try {
                FileWriter fw = new FileWriter(file);
                fw.write(str);
                fw.flush();
                fw.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        //modify xml, add query list method
        GeneratedXmlFile xmlFile = xmllist.get(0);

        java.lang.reflect.Field documentField = GeneratedXmlFile.class.
                getDeclaredField("document");

        documentField.setAccessible(true);

        Document document = (Document) documentField.get(xmlFile);

        XmlElement ele = (XmlElement) document.getRootElement().getElements().get(0);

        XmlElement ele2 = (XmlElement) document.getRootElement().getElements().get(2);
        Map<String, String> properties = new HashMap<String, String>();
        for (Element element: ele.getElements()) {
            XmlElement xmlElement = (XmlElement) element;
            String key = "";
            String value = "";
            for (int i = xmlElement.getAttributes().size()-1; i >= 0; i--) {
                Attribute attribute = xmlElement.getAttributes().get(i);
                if ("property".equals(attribute.getName())) {
                    key = attribute.getValue();
                }
                if ("column".equals(attribute.getName())) {
                    value = attribute.getValue();
                }
            }
            properties.put(key, value);
        }

        String xmlString = "<select id=\"query" + fileName + "List\" resultMap=\"BaseResultMap\">\n" +
                "    select\n" +
                "    <include refid=\"Base_Column_List\" />\n" +
                "" + ele2.getElements().get(2).getFormattedContent(2) + "\n" +
                "    where 1 = 1\n";
        for (String property: properties.keySet()) {
            xmlString += "    <if test=\"" + property + " != null\">\n" +
                    "      and " + properties.get(property) + " = #{" + property + "}\n" +
                    "    </if>\n";
        }
        xmlString += "    order by id desc limit #{start},#{limit}\n" +
                "  </select>\n" +
                "\n" +
                "  <select id=\"query" + fileName + "Count\" resultType=\"java.lang.Integer\">\n" +
                "    select\n" +
                "    count(1)\n" +
                "" + ele2.getElements().get(2).getFormattedContent(2) + "\n" +
                "    where 1 = 1\n";
        for (String property: properties.keySet()) {
            xmlString += "    <if test=\"" + property + " != null\">\n" +
                    "      and " + properties.get(property) + " = #{" + property + "}\n" +
                    "    </if>\n";
        }
        xmlString += "  </select></mapper>";

        File mapperFile = new File(mapperConfig.getTargetProject() + "\\" + mapperConfig.getTargetPackage().replace(".", "\\") + "\\" + fileName + "Mapper.xml");
        InputStream in = new FileInputStream(mapperFile);
        String xmlStr = MyStringUtils.convertStreamToString(in);
        xmlString = xmlStr.replace("</mapper>", "") + xmlString;
        if (null != xmlString && !"".equals(xmlString)) {
            try {
                FileWriter fw = new FileWriter(mapperFile);
                fw.write(xmlString);
                fw.flush();
                fw.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }


        String fileNameLower = MyStringUtils.toLowerCaseFirstOne(fileName);
        String fileReqLower = MyStringUtils.toLowerCaseFirstOne(fileReq);
        String controllerStr = "package " + modelConfig.getTargetPackage() + ";\n"
                + "\nimport " + mapperConfig.getTargetPackage() + "." + fileName +"Mapper;\n"
                + "import com.tuniu.asr.intf.entity." + fileName + ";\n" +
                "import com.tuniu.asr.intf.entity." + fileName + "Req;\n" +
                "import com.tuniu.operation.platform.tsg.base.core.annotation.Json;\n" +
                "import com.tuniu.operation.platform.tsg.base.core.utils.JsonUtil;\n" +
                "import com.tuniu.operation.platform.tsg.base.core.utils.ResponseVo;\n" +
                "import org.apache.commons.codec.binary.Base64;\n" +
                "import org.slf4j.Logger;\n" +
                "import org.slf4j.LoggerFactory;\n" +
                "import org.springframework.stereotype.Controller;\n" +
                "import org.springframework.web.bind.annotation.RequestMapping;\n" +
                "import org.springframework.web.bind.annotation.RequestMethod;\n" +
                "import org.springframework.web.bind.annotation.ResponseBody;\n" +
                "\n" +
                "import javax.annotation.Resource;\n" +
                "import javax.servlet.http.HttpServletRequest;\n" +
                "import javax.servlet.http.HttpServletResponse;\n" +
                "import java.io.IOException;\n" +
                "import java.io.PrintWriter;\n" +
                "import java.util.HashMap;\n" +
                "import java.util.List;\n" +
                "import java.util.Map;\n" +
                "\n" +
                "@Controller\n" +
                "@RequestMapping(\"/" + fileNameLower + "\")"
                + "\npublic class " + fileName + "Controller" + " {\n"
                + "    private static final Logger LOGGER = LoggerFactory.getLogger(" + fileName + "Controller.class);\n"
                + "\n"
                + "    @Resource\n"
                + "    private "+ fileName +"Mapper " + fileNameLower + "Mapper;\n"
                + "\n"
                + "@RequestMapping(value = \"/query\", method = RequestMethod.GET)\n"
                + "@ResponseBody\n"
                + "public void query" + fileName + "List(@Json " + fileReq + " " + fileReqLower + ", HttpServletRequest request,\n"
                + "                HttpServletResponse response) throws IOException {\n"
                + "    PrintWriter writer = null;\n"
                + "    ResponseVo responseVo = new ResponseVo();\n"
                + "    response.setHeader(\"Access-Control-Allow-Origin\", \"*\");\n"
                + "    writer = response.getWriter();\n"
                + "    try {\n"
                + "        LOGGER.info(\"query" + fileName + "List param:{}\", JsonUtil.toString(" + fileReqLower + "));\n"
                + "        List<" + fileName + "> " + fileNameLower + "List = " + fileNameLower + "Mapper.query" + fileName + "List(" + fileReqLower + ");\n"
                + "        int count = " + fileNameLower + "Mapper.query" + fileName + "Count(" + fileReqLower + ");\n"
                + "        Map<String, Object> map = new HashMap<String, Object>();\n"
                + "        map.put(\"count\", count);\n"
                + "        map.put(\"rows\", " + fileNameLower + "List);\n"
                + "        responseVo.setData(map);\n"
                + "        responseVo.setSuccess(true);\n"
                + "    } catch (Exception e) {\n"
                + "        LOGGER.error(\"查询异常\", e);\n"
                + "        responseVo.setSuccess(false);\n"
                + "        responseVo.setMsg(\"查询异常\");\n"
                + "    }\n"
                + "    LOGGER.info(\"query" + fileName + "List result:{}\", JsonUtil.toString(responseVo));\n"
                + "    writer.print(Base64.encodeBase64String(JsonUtil.toString(responseVo).getBytes(\"utf-8\")));\n"
                + "}\n";

        controllerStr += "    @RequestMapping(value = \"/save\", method = RequestMethod.POST)\n" +
                "    @ResponseBody\n" +
                "    public void save" + fileName + "(@Json " + fileName + " " + fileNameLower + ", HttpServletRequest request,\n" +
                "                                HttpServletResponse response) throws IOException {\n" +
                "        PrintWriter writer = null;\n" +
                "        ResponseVo responseVo = new ResponseVo();\n" +
                "        response.setHeader(\"Access-Control-Allow-Origin\", \"*\");\n" +
                "        writer = response.getWriter();\n" +
                "        try {\n" +
                "            LOGGER.info(\"save" + fileName + "'s param:{}\", JsonUtil.toString(" + fileNameLower + "));\n" +
                "            int count = " + fileNameLower + "Mapper.insertSelective(" + fileNameLower + ");\n" +
                "            responseVo.setData(count);\n" +
                "            responseVo.setSuccess(true);\n" +
                "        } catch (Exception e) {\n" +
                "            LOGGER.error(\"保存异常\", e);\n" +
                "            responseVo.setMsg(\"保存异常\");\n" +
                "            responseVo.setSuccess(false);\n" +
                "        }\n" +
                "        writer.print(Base64.encodeBase64String(JsonUtil.toString(responseVo).getBytes(\"utf-8\")));\n" +
                "    }\n";

        controllerStr += "    @RequestMapping(value = \"/findById\", method = RequestMethod.POST)\n" +
                "    @ResponseBody\n" +
                "    public void find" + fileName + "ById(@Json " + fileName + " " + fileNameLower + ", HttpServletRequest request,\n" +
                "                                     HttpServletResponse response) throws IOException {\n" +
                "        PrintWriter writer = null;\n" +
                "        ResponseVo responseVo = new ResponseVo();\n" +
                "        response.setHeader(\"Access-Control-Allow-Origin\", \"*\");\n" +
                "        writer = response.getWriter();\n" +
                "        try {\n" +
                "            " + fileName + " " + fileNameLower +"Entity = " + fileNameLower + "Mapper.selectByPrimaryKey(" + fileNameLower + ".getId());\n" +
                "            responseVo.setData(" + fileNameLower + "Entity);\n" +
                "            responseVo.setSuccess(true);\n" +
                "        }catch (Exception e) {\n" +
                "            LOGGER.error(\"查询单条记录异常\", e);\n" +
                "            responseVo.setMsg(\"查询单条记录异常\");\n" +
                "            responseVo.setSuccess(false);\n" +
                "        }\n" +
                "        writer.print(Base64.encodeBase64String(JsonUtil.toString(responseVo).getBytes(\"utf-8\")));\n" +
                "    }\n";
        controllerStr += "    @RequestMapping(value=\"/delete\", method = RequestMethod.POST)\n" +
                "    @ResponseBody\n" +
                "    public void delete" + fileName + "(@Json " + fileReq + " " + fileReqLower + ", HttpServletRequest request,\n" +
                "                               HttpServletResponse response) throws IOException {\n" +
                "        PrintWriter writer = null;\n" +
                "        ResponseVo responseVo = new ResponseVo();\n" +
                "        response.setHeader(\"Access-Control-Allow-Origin\", \"*\");\n" +
                "        writer = response.getWriter();\n" +
                "        try {\n" +
                "            LOGGER.info(\"delete" + fileName + "'s param:{}\", JsonUtil.toString(" + fileReqLower + "));\n" +
                "            Long[] idList = " + fileReqLower + ".getIdList();\n" +
                "            for (Long id : idList) {\n" +
                "                " + fileNameLower + "Mapper.deleteByPrimaryKey(id);\n" +
                "            }\n" +
                "            responseVo.setSuccess(true);\n" +
                "        }catch (Exception e) {\n" +
                "            LOGGER.error(\"删除异常\", e);\n" +
                "            responseVo.setMsg(\"删除异常\");\n" +
                "            responseVo.setSuccess(false);\n" +
                "        }\n" +
                "        writer.print(Base64.encodeBase64String(JsonUtil.toString(responseVo).getBytes(\"utf-8\")));\n" +
                "    }\n";

        controllerStr += "@RequestMapping(value=\"/update\", method = RequestMethod.POST)\n" +
                "    @ResponseBody\n" +
                "    public void update" + fileName + " (@Json " + fileName + " " + fileNameLower + ", HttpServletRequest request,\n" +
                "                              HttpServletResponse response) throws IOException {\n" +
                "        PrintWriter writer = null;\n" +
                "        ResponseVo responseVo = new ResponseVo();\n" +
                "        response.setHeader(\"Access-Control-Allow-Origin\", \"*\");\n" +
                "        writer = response.getWriter();\n" +
                "        try {\n" +
                "            LOGGER.info(\"update" + fileName + "'s param:{}\", JsonUtil.toString(" + fileNameLower + "));\n" +
                "            int count = " + fileNameLower + "Mapper.updateByPrimaryKeySelective(" + fileNameLower + ");\n" +
                "            responseVo.setData(count);\n" +
                "            responseVo.setSuccess(true);\n" +
                "        } catch (Exception e) {\n" +
                "            LOGGER.error(\"修改异常\", e);\n" +
                "            responseVo.setMsg(\"修改异常\");\n" +
                "            responseVo.setSuccess(false);\n" +
                "        }\n" +
                "        writer.print(Base64.encodeBase64String(JsonUtil.toString(responseVo).getBytes(\"utf-8\")));\n" +
                "    }\n";

        controllerStr += "}\n";
        File controllerFile = new File(modelConfig.getTargetProject() + "\\" + fileName + "Controller.java");
        if (null != controllerStr && !"".equals(controllerStr)) {
            try {
                FileWriter fw = new FileWriter(controllerFile);
                fw.write(controllerStr);
                fw.flush();
                fw.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        File mapperJavaFile = new File(daoConfig.getTargetProject() + "\\" + daoConfig.getTargetPackage().replace(".", "\\") + "\\" + fileName + "Mapper.java");
        InputStream input = new FileInputStream(mapperJavaFile);
        String javaStr = MyStringUtils.convertStreamToString(input);
        javaStr = javaStr.substring(javaStr.length() -1) + "List<" + fileName + "> query" + fileName + "List(" + fileName + "Req " + fileNameLower + "Req);\n" +
                "\n" +
                "    int query" + fileName + "Count(" + fileName + "Req " + fileNameLower + "Req);\n"
                + "}";
        if (null != javaStr && !"".equals(javaStr)) {
            try {
                FileWriter fw = new FileWriter(mapperJavaFile);
                fw.write(javaStr);
                fw.flush();
                fw.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        String uedStr = "<!DOCTYPE HTML>\n" +
                "<html>\n" +
                "\n" +
                "<head>\n" +
                "    <meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">\n" +
                "    <title>查询</title>\n" +
                "    <script type=\"text/javascript\" src=\"../../../jsrc/modules/importCss.js\"></script>\n" +
                "    <link href=\"../css/reset.css\" rel=\"stylesheet\" type=\"text/css\">\n" +
                "    <script type='text/javascript' src='../../../jsrc/modules/jquery-1.7.2.js'></script>\n" +
                "    <script type=\"text/javascript\" src=\"../../../jsrc/modules/importJS.js\"></script>\n" +
                "    <script type='text/javascript' src='../../../jsrc/modules/tn/TNQuoteGridPanel/TNQuoteGridPanel.js'></script>\n" +
                "    <script type=\"text/javascript\" src=\"../../../jsrc/modules/tn/TNSearch/DynamicTNSearch.js\"></script>\n" +
                "    <script>\n" +
                "    $import(\"com.tuniu.portal.PortalApp\", function(PortalApp) {\n" +
                "        $(function() {\n" +
                "            PortalApp.getInstance().init(\""+ fileName + "List\", \"FLT\", \"NAP\");\n" +
                "        });\n" +
                "    });\n" +
                "    </script>\n" +
                "    <style>\n" +
                "    .padding {\n" +
                "        padding-left: 20px;\n" +
                "    }\n" +
                "    \n" +
                "    html {\n" +
                "        overflow-x: scroll !important;\n" +
                "        min-width: 1360px;\n" +
                "    }\n" +
                "    \n" +
                "    .select-medium {\n" +
                "        height: 29px;\n" +
                "        width: 154px;\n" +
                "    }\n" +
                "    \n" +
                "    .input-mini-cell {\n" +
                "        width: 65px;\n" +
                "    }\n" +
                "    \n" +
                "    .tngpBody td {\n" +
                "        font-family: Monaco;\n" +
                "    }\n" +
                "    \n" +
                "    .addTr {\n" +
                "        margin: 5px 0 5px 0;\n" +
                "    }\n" +
                "    </style>\n" +
                "</head>\n" +
                "\n" +
                "<body>\n" +
                "    <header>\n" +
                "        <ul class=\"breadcrumb\">\n" +
                "            <li>\n" +
                "                <a href=\"#\">首页</a>\n" +
                "                <span class=\"divider\">/</span>\n" +
                "            </li>\n" +
                "            <li>\n" +
                "                供应链系统\n" +
                "                <span class=\"divider\">/</span>\n" +
                "            </li>\n" +
                "            <li>\n" +
                "                资源子系统\n" +
                "                <span class=\"divider\">/</span>\n" +
                "            </li>\n" +
                "            <li>\n" +
                "                分销订单系统\n" +
                "                <span class=\"divider\">/</span>\n" +
                "            </li>\n" +
                "            <li>列表</li>\n" +
                "        </ul>\n" +
                "    </header>\n" +
                "    <div class=\"container-fluid\">\n" +
                "        <fieldset>\n" +
                "            <legend>查询</legend>\n" +
                "            <div class=\"grid-info search\">\n" +
                "                <form id=\"queryForm\">\n" +
                "                    <table class=\"grid-table\">\n" +
                "                        <tr>\n";
        TopLevelClass topLevelClass = (TopLevelClass) entityFile.getCompilationUnit();
        Map<String, String> fieldMap = new HashMap<String, String>();
        for (Field field:topLevelClass.getFields()) {
            String fieldName = field.getName();
            System.out.print(fieldName);
            if ("id".equals(fieldName) || "addTime".equals(fieldName) || "updateTime".equals(fieldName)
                    || "delFlag".equals(fieldName)) {
                continue;
            }
            if (field.getJavaDocLines().size() == 0) {
                continue;
            }

            String secondDocLine = field.getJavaDocLines().get(1);
            secondDocLine = secondDocLine.replace(" * ", "");
            if (secondDocLine.indexOf(",") != -1) {
                secondDocLine = secondDocLine.substring(0, secondDocLine.indexOf(","));
            }
            if (secondDocLine.indexOf(" ") != -1) {
                secondDocLine = secondDocLine.substring(0, secondDocLine.indexOf(" "));
            }

            fieldMap.put(field.getName(),secondDocLine);

            uedStr += "                          <th class='padding'>" + secondDocLine + ":</th><td>\n" +
                    "                                <input id=\"" + field.getName() + "\" name=\"" + field.getName() + "\" class=\"input-medium\" type=\"text\" style=\"padding-left:1px;padding-right:1px;\" />\n" +
                    "                            </td>\n";
        }
        uedStr += "                        </tr>\n" +
                "                    </table>\n" +
                "                </form>\n" +
                "                <a id=\"searchBtn\" class=\"btn btn-primary searchBtn\" style=\"margin:0 40px 10px 40px;\"><i class=\"icon-search\"></i> 搜索\n" +
                "                </a>\n" +
                "                <a id=\"clearBtn\" class=\"btn btn-primary\" style=\"margin:0 0 10px 20px;\"><i class=\"icon-trash\"></i> 清除筛选\n" +
                "                </a>\n" +
                "            </div>\n" +
                "        </fieldset>\n" +
                "        <fieldset>\n" +
                "            <legend>列表</legend>\n" +
                "            <div style=\"margin:0;padding:0\">\n" +
                "                <table>\n" +
                "                    <tr>\n" +
                "                        <td>\n" +
                "                            <a id=\"addBtn\" class=\"btn btn-tool\" style=\"margin-top:10px;\"> <i class=\"icon-plus\"></i> 新增\n" +
                "                            </a>\n" +
                "                        </td>\n" +
                "                    </tr>\n" +
                "                </table>\n" +
                "            </div>\n" +
                "            <div id=\"" + fileNameLower + "List\" style='margin-top:5px;'></div>\n" +
                "        </fieldset>\n" +
                "    </div>\n" +
                "    <div id=\"createContent\" style=\"display:none;\">\n" +
                "        <fieldset>\n" +
                "            <legend>新增页面</legend>\n" +
                "            <form autocomplete=\"off\">\n" +
                "                <table id=\"detailInputs\" style=\"border-collapse:separate; border-spacing:10px;\">\n";

        for (Field field:topLevelClass.getFields()) {
            String fieldName = field.getName();
            System.out.print(fieldName);
            if ("id".equals(fieldName) || "addTime".equals(fieldName) || "updateTime".equals(fieldName)
                    || "delFlag".equals(fieldName)) {
                continue;
            }
            if (field.getJavaDocLines().size() == 0) {
                continue;
            }
            String secondDocLine = field.getJavaDocLines().get(1);
            secondDocLine = secondDocLine.replace(" * ", "");
            if (secondDocLine.indexOf(",") != -1) {
                secondDocLine = secondDocLine.substring(0, secondDocLine.indexOf(","));
            }
            if (secondDocLine.indexOf(" ") != -1) {
                secondDocLine = secondDocLine.substring(0, secondDocLine.indexOf(" "));
            }
            uedStr += "                    <tr>\n" +
                    "                        <th>" + secondDocLine + ":</th>\n" +
                    "                        <td>\n" +
                    "                            <input type=\"text\" name=\"" + field.getName() + "\" id=\"" + field.getName() + "\" class=\"input-size validate\" placeholder=\"\" />\n" +
                    "                        </td>\n" +
                    "                    </tr>\n";
        }

        uedStr += "                </table>\n" +
                "            </form>\n" +
                "        </fieldset>\n" +
                "    </div>\n" +
                "    <footer></footer>\n" +
                "</body>\n" +
                "\n" +
                "</html>";

        File uedFile = new File(modelConfig.getTargetProject() + "\\" + fileName + "List.html");
        if (null != uedStr && !"".equals(uedStr)) {
            try {
                FileWriter fw = new FileWriter(uedFile);
                fw.write(uedStr);
                fw.flush();
                fw.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        String listJsStr = "function " + fileName + "List(config) {\n" +
                "    $.extend(this, config, {\n" +
                "        noty: new Noty(),\n" +
                "        ver: new Ver()\n" +
                "    });\n" +
                "    this.init();\n" +
                "}\n" +
                "\n" +
                "$.extend(" + fileName + "List.prototype, {\n" +
                "    init: function() {\n" +
                "        $(document).ready(function() {\n" +
                "            if (tn.Base64.decode(tn.cookie.get(\"honeydukesUname\")) == \"\" || tn.cookie.get(\"honeydukesUid\") == \"\") {\n" +
                "                window.location.href = \"../../../login/welcome.html\";\n" +
                "            }\n" +
                "        });\n" +
                "        //限制fieldset宽度\n" +
                "        this.render.gridAuto.call(this);\n" +
                "\n" +
                "        var self = this;\n" +
                "        //加载列表\n" +
                "        var " + fileNameLower + "List = self." + fileNameLower + "List = new TNGP(self." + fileNameLower + "ListConf($(\"#" + fileNameLower + "List\"))).tngp();\n" +
                "        //绑定页面事件\n" +
                "        self.bindEvent.btnClick.call(self);\n" +
                "        self.reloadList.call(self);\n" +
                "    },\n" +
                "\n" +
                "\n" +
                "    /**\n" +
                "     * 页面渲染\n" +
                "     */\n" +
                "    render: {\n" +
                "        //固定fieldset内列表宽度\n" +
                "        gridAuto: function() {\n" +
                "            var w = $(\".container-fluid\").width() - 40;\n" +
                "            var gridBox = $(\".container-fluid\").find(\".grid-auto\");\n" +
                "            gridBox.width(w);\n" +
                "            $(window).resize(function() {\n" +
                "                var w = $(\".container-fluid\").width() - 40;\n" +
                "                var gridBox = $(\".container-fluid\").find(\".grid-auto\");\n" +
                "                gridBox.width(w);\n" +
                "            });\n" +
                "        }\n" +
                "    },\n" +
                "\n" +
                "\n" +
                "    bindPageData: {\n" +
                "        showSavePage: function(data) {\n" +
                "            var self = this;\n" +
                "            self.showContent = $(\"#createContent\").clone();\n" +
                "            self.showWindow(\"" + fileName + "Create\", self, { \"operate\": \"add\" });\n" +
                "        }\n" +
                "    },\n" +
                "\n" +
                "    /**\n" +
                "     * 页面事件绑定\n" +
                "     */\n" +
                "    bindEvent: {\n" +
                "        btnClick: function() {\n" +
                "            var self = this;\n" +
                "            //查询按钮\n" +
                "            $(\"#searchBtn\").unbind(\"click\").click(function(e) {\n" +
                "                self.reloadList.call(self);\n" +
                "            });\n" +
                "            $(\"#clearBtn\").unbind(\"click\").click(function(e) {\n" +
                "                self.clearFliterParam.call(self);\n" +
                "            });\n" +
                "            //新增按钮\n" +
                "            $(\"#addBtn\").unbind(\"click\").click(function(e) {\n" +
                "                self.bindPageData.showSavePage.call(self);\n" +
                "            });\n" +
                "        }\n" +
                "    },\n" +
                "\n" +
                "    /**\n" +
                "     * 列表\n" +
                "     */\n" +
                "    " + fileNameLower + "ListConf: function(element) {\n" +
                "        var self = this;\n" +
                "        var linkUrl = self.getAction().linkUrl;\n" +
                "        var tableConfig = {\n" +
                "            url: self.getAction().get" + fileName + "List,\n" +
                "            type: \"GET\",\n" +
                "            el: element,\n" +
                "            colModel: [{\n" +
                "                display: 'checkbox',\n" +
                "                name: 'checkbox',\n" +
                "                width: 20,\n" +
                "                handler: function(v, data, n, tr, index) {\n" +
                "                    n.html($(\"<input type='checkbox'/>\"));\n" +
                "                }\n" +
                "            },";
        for (Field field:topLevelClass.getFields()) {
            String fieldName = field.getName();
            System.out.print(fieldName);
            if (field.getJavaDocLines().size() == 0) {
                continue;
            }
            String secondDocLine = field.getJavaDocLines().get(1);
            secondDocLine = secondDocLine.replace(" * ", "");
            if (secondDocLine.indexOf(",") != -1) {
                secondDocLine = secondDocLine.substring(0, secondDocLine.indexOf(","));
            }
            if (secondDocLine.indexOf(" ") != -1) {
                secondDocLine = secondDocLine.substring(0, secondDocLine.indexOf(" "));
            }
            if ("id".equals(fieldName)) {
                listJsStr += "{\n" +
                        "                display: '序号',\n" +
                        "                name: 'id',\n" +
                        "                width: 60,\n" +
                        "                handler: function(v, data, n) {\n" +
                        "                    n.html(\"<a style='cursor:pointer;'>\" + data.id + \"</a>\");\n" +
                        "                    n.find('a').click(function() {\n" +
                        "                        self.showContent = $(\"#createContent\").clone();\n" +
                        "                        self.showWindow(\"" + fileName + "Create\", self, { \"operate\": \"view\", \"data\": data });\n" +
                        "                    });\n" +
                        "                }\n" +
                        "            },";
            } else if ("addTime".equals(fieldName)) {
                listJsStr += "{\n" +
                        "                display: '录入时间',\n" +
                        "                name: 'addTime',\n" +
                        "                width: 80,\n" +
                        "                handler: function(v, data, n, i, row) {\n" +
                        "                    var createAt = data.addTime;\n" +
                        "                    if (createAt) {\n" +
                        "                        var index = createAt.indexOf(\" \");\n" +
                        "                        var date = createAt.substring(0, index);\n" +
                        "                        var time = createAt.substring(index + 1, createAt.length);\n" +
                        "                        var html = date + \"<br/>\" + time;\n" +
                        "                        n.attr(\"title\", createAt);\n" +
                        "                        n.html(html);\n" +
                        "                    } else {\n" +
                        "                        createAt = \"/\";\n" +
                        "                        n.attr(\"title\", createAt);\n" +
                        "                        n.html(createAt);\n" +
                        "                    }\n" +
                        "                }\n" +
                        "            },";
            } else if ("updateTime".equals(fieldName)) {
                listJsStr += "{\n" +
                        "                display: '更新时间',\n" +
                        "                name: 'updateTime',\n" +
                        "                width: 80,\n" +
                        "                handler: function(v, data, n, i, row) {\n" +
                        "                    var createAt = data.updateTime;\n" +
                        "                    if (createAt) {\n" +
                        "                        var index = createAt.indexOf(\" \");\n" +
                        "                        var date = createAt.substring(0, index);\n" +
                        "                        var time = createAt.substring(index + 1, createAt.length);\n" +
                        "                        var html = date + \"<br/>\" + time;\n" +
                        "                        n.attr(\"title\", createAt);\n" +
                        "                        n.html(html);\n" +
                        "                    } else {\n" +
                        "                        createAt = \"/\";\n" +
                        "                        n.attr(\"title\", createAt);\n" +
                        "                        n.html(createAt);\n" +
                        "                    }\n" +
                        "                }\n" +
                        "            },";
            }else if ("delFlag".equals(fieldName)) {
                continue;
            } else {
                listJsStr += "{\n                display: '" + secondDocLine + "',\n" +
                        "                name: '" + field.getName() + "',\n" +
                        "                width: 80,\n" +
                        "                handler: function(v, data, n, i, row) {\n" +
                        "\n" +
                        "                }\n" +
                        "            },";
            }
        }


            listJsStr += "{\n" +
                    "                display: '操作',\n" +
                    "                name: 'operate',\n" +
                    "                width: 180,\n" +
                    "                handler: function(v, data, n, i, row) {\n" +
                    "                    //复制\n" +
                    "                    var copy = $(\"<a><i class ='icon-share'></i>复制</a>\").click(function() {\n" +
                    "                        self.showContent = $(\"#createContent\").clone();\n" +
                    "                        self.showWindow(\"" + fileName + "Create\", self, { \"operate\": \"copy\", \"data\": data });\n" +
                    "                    });\n" +
                    "                    //编辑\n" +
                    "                    var edit = $(\"<a><i class='icon-edit'></i>编辑</a>\").click(function() {\n" +
                    "                        self.showContent = $(\"#createContent\").clone();\n" +
                    "                        self.showWindow(\"" + fileName + "Create\", self, { \"operate\": \"edit\", \"data\": data });\n" +
                    "                    });\n" +
                    "                    //删除\n" +
                    "                    var del = $(\"<a><i class ='icon-trash'></i>删除</a>\").click(function() {\n" +
                    "                        self.delFun.call(self, row, data);\n" +
                    "                    });\n" +
                    "                    n.append(copy);\n" +
                    "                    n.append(edit);\n" +
                    "                    n.append(del);\n" +
                    "                }\n" +
                    "            }],\n" +
                    "            showToggleBtn: false,\n" +
                    "            height: \"auto\",\n" +
                    "            autoload: false\n" +
                    "        };\n" +
                    "        return tableConfig;\n" +
                    "    },\n" +
                    "\n" +
                    "\n" +
                    "    buildQueryPram: function() {\n" +
                    "        var self = this;\n" +
                    "        var queryParam = self.util.form.get($(\".search\"));\n" +
                    "        return queryParam;\n" +
                    "    },\n" +
                    "    /**\n" +
                    "     * 用于加载列表数据的方法\n" +
                    "     */\n" +
                    "    reloadList: function() {\n" +
                    "        var self = this;\n" +
                    "        self." + fileNameLower + "List.reload(self.buildQueryPram());\n" +
                    "    },\n" +
                    "\n" +
                    "    clearFliterParam: function() {\n" +
                    "        $(\"input\").val(\"\");\n" +
                    "    },\n" +
                    "\n" +
                    "    //单个删除方法\n" +
                    "    delFun: function(row, data) {\n" +
                    "        var self = this;\n" +
                    "        var effectParam = [];\n" +
                    "        effectParam.push(data.id);\n" +
                    "        if (!effectParam) {\n" +
                    "            return;\n" +
                    "        }\n" +
                    "        self.noty.confirm(\"您确定要进行删除操作吗？\", {\n" +
                    "            type: \"btn btn-primary\",\n" +
                    "            text: \"<i class='icon-ok'></i>确定\",\n" +
                    "            click: function(noty) {\n" +
                    "                tn.ajax.request({\n" +
                    "                    type: \"POST\",\n" +
                    "                    url: self.getAction().batchDelete,\n" +
                    "                    data: {\n" +
                    "                        idList: effectParam,\n" +
                    "                        opUid: tn.cookie.get(\"honeydukesUid\"),\n" +
                    "                        opName: Base64.decode(tn.cookie.get(\"honeydukesUname\"))\n" +
                    "                    },\n" +
                    "                    listener: {\n" +
                    "                        success: function(json) {\n" +
                    "                            if (json.success) {\n" +
                    "                                self.noty.info(\"操作成功\");\n" +
                    "                                setTimeout(function() { self.reloadList.call(self) }, 1000);\n" +
                    "                            } else {\n" +
                    "                                self.noty.info(\"操作失败\");\n" +
                    "                            }\n" +
                    "                        }\n" +
                    "                    }\n" +
                    "                });\n" +
                    "                noty.close();\n" +
                    "            }\n" +
                    "        });\n" +
                    "    },\n" +
                    "\n" +
                    "    /**\n" +
                    "     * 批量删除方法\n" +
                    "     */\n" +
                    "    batchDelete: function() {\n" +
                    "        var self = this;\n" +
                    "        var batchEffectParam = self.getBatchParamIds();\n" +
                    "        if (!batchEffectParam) {\n" +
                    "            return;\n" +
                    "        }\n" +
                    "        self.noty.confirm(\"您确定要进行批量删除操作吗？\", {\n" +
                    "            type: \"btn btn-primary\",\n" +
                    "            text: \"<i class='icon-ok'></i>确定\",\n" +
                    "            click: function(noty) {\n" +
                    "                tn.ajax.request({\n" +
                    "                    type: \"POST\",\n" +
                    "                    url: self.getAction().batchDelete,\n" +
                    "                    data: {\n" +
                    "                        idList: batchEffectParam,\n" +
                    "                        opUid: tn.cookie.get(\"honeydukesUid\"),\n" +
                    "                        opName: Base64.decode(tn.cookie.get(\"honeydukesUname\"))\n" +
                    "                    },\n" +
                    "                    listener: {\n" +
                    "                        success: function(json) {\n" +
                    "                            if (json.success) {\n" +
                    "                                self.noty.info(\"操作成功\");\n" +
                    "                            } else {\n" +
                    "                                self.noty.info(\"操作失败\");\n" +
                    "                            }\n" +
                    "                            setTimeout(function() { self.reloadList.call(self) }, 1000);\n" +
                    "                        },\n" +
                    "                        requestcomplete: function() {\n" +
                    "                            $(\"#" + fileNameLower + "List input[type='checkbox']\").attr(\"checked\", false);\n" +
                    "                        },\n" +
                    "                        error: function() {\n" +
                    "                            self.noty.error(\"接口调用异常\");\n" +
                    "                        }\n" +
                    "                    }\n" +
                    "                });\n" +
                    "                noty.close();\n" +
                    "            }\n" +
                    "        });\n" +
                    "\n" +
                    "    },\n" +
                    "    //\n" +
                    "    getBatchParamIds: function() {\n" +
                    "        var self = this;\n" +
                    "        var arr = this." + fileNameLower + "List.getCheckedRowsData();\n" +
                    "        var ids = [];\n" +
                    "        $.each(arr, function(i, item) {\n" +
                    "            ids.push(item.id);\n" +
                    "        });\n" +
                    "        if (ids.length <= 0) {\n" +
                    "            self.noty.error(\"请勾选\");\n" +
                    "            return;\n" +
                    "        }\n" +
                    "        return ids;\n" +
                    "    },\n" +
                    "\n" +
                    "    /**\n" +
                    "     * 页面AJAX请求URL统一处理\n" +
                    "     */\n" +
                    "    getAction: function() {\n" +
                    "        if (this.devcfg) {\n" +
                    "            return {\n" +
                    "                get" + fileName + "List: this.protectedSystem.server.ASR + \"" + fileNameLower + "/query\",\n" +
                    "                batchDelete: this.protectedSystem.server.ASR + \"" + fileNameLower + "/delete\",\n" +
                    "            };\n" +
                    "        } else {\n" +
                    "            return {\n" +
                    "                get" + fileName + "List: this.protectedSystem.server.ASR + \"" + fileNameLower + "/query\",\n" +
                    "                batchDelete: this.protectedSystem.server.ASR + \"" + fileNameLower + "/delete\",\n" +
                    "            };\n" +
                    "        }\n" +
                    "    }\n" +
                    "});\n";

        File listJsFile = new File(modelConfig.getTargetProject() + "\\" + fileName + "List.js");
        if (null != listJsStr && !"".equals(listJsStr)) {
            try {
                FileWriter fw = new FileWriter(listJsFile);
                fw.write(listJsStr);
                fw.flush();
                fw.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        String formJsStr = "function " + fileName + "Create(config, datas) {\n" +
                "    var win;\n" +
                "    var html = config.showContent;\n" +
                "    html.show();\n" +
                "    var $detailContent = config.showContent;\n" +
                "    initWindow(datas);\n" +
                "    \n" +
                "    $detailContent.find(\"legend\").text(initFieldSet(datas));\n" +
                "    var buttons = initWindowButtons(datas);\n" +
                "    var title=initWindowTitle(datas);\n" +
                "    win = new WinForm({\n" +
                "        title: title,\n" +
                "        node: html,\n" +
                "        cancelText: '关闭',\n" +
                "        css: {\n" +
                "            top: \"50px\",\n" +
                "            width: \"30%\",\n" +
                "            left: \"35%\"\n" +
                "        },\n" +
                "        buttons: buttons\n" +
                "    });\n" +
                "    //最后时刻返回win对象\n" +
                "    return win;\n" +
                "    $.extend(this, config, {\n" +
                "        noty: new Noty(),\n" +
                "        ver: new Ver()\n" +
                "    });\n" +
                "    /**\n" +
                "     *功能说明 初始化窗口\n" +
                "     */\n" +
                "    function initWindow(datas) {\n" +
                "        if (datas.operate != \"add\") {\n" +
                "            showData(datas.data);\n" +
                "        }\n" +
                "\n" +
                "        if (datas.operate == \"edit\") {\n" +
                "            $detailContent.find(\"input\").removeAttr(\"disabled\", true);\n" +
                "        }\n" +
                "        if (datas.operate == \"view\") {\n" +
                "            $detailContent.find(\"input\").attr(\"disabled\", true);\n" +
                "        } else {\n" +
                "            bindBlurFunction();\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "    function initWindowButtons(datas) {\n" +
                "        var buttons = [{\n" +
                "            name: \"<i class='icon-ok'></i>确认\",\n" +
                "            class: \"btn btn-primary occupy trackAnaly_272\",\n" +
                "            click: function() {\n" +
                "                var errorItem = $(\".ver-error\");\n" +
                "                if (errorItem.length < 1) {\n" +
                "                    if (datas.operate == \"add\" || datas.operate == \"copy\") {\n" +
                "                        save(datas);\n" +
                "                    } else if (datas.operate == \"edit\") {\n" +
                "                        update(datas);\n" +
                "                    }\n" +
                "                }\n" +
                "            }\n" +
                "        }, {\n" +
                "            name: \"<i class='icon-refresh'></i>重置\",\n" +
                "            class: \"btn btn-primary occupy trackAnaly_272\",\n" +
                "            click: function() {\n" +
                "                reset(datas);\n" +
                "            }\n" +
                "        }, {\n" +
                "            name: \"<i class='icon-remove'></i>关闭\",\n" +
                "            class: \"btn btn-danger\",\n" +
                "            click: function() {\n" +
                "                win.hide();\n" +
                "            }\n" +
                "        }];\n" +
                "        var lastButtons = [];\n" +
                "        if (datas.operate == \"edit\") {\n" +
                "            lastButtons.push(buttons[0]);\n" +
                "            lastButtons.push(buttons[2]);\n" +
                "        } else if (datas.operate == \"view\") {\n" +
                "        } else {\n" +
                "            lastButtons = buttons;\n" +
                "        }\n" +
                "        return lastButtons;\n" +
                "    }\n" +
                "\n" +
                "    /**\n" +
                "     *功能说明 初始化窗口标题\n" +
                "     */\n" +
                "    function initWindowTitle(datas) {\n" +
                "        var title = \"\";\n" +
                "        if (datas.operate == \"add\") {\n" +
                "            title += \"新增\";\n" +
                "        } else if (datas.operate == \"edit\") {\n" +
                "            title += \"编辑\";\n" +
                "        } else if (datas.operate == \"view\") {\n" +
                "            title += \"详情\";\n" +
                "        }\n" +
                "        return title;\n" +
                "    }\n" +
                "\n" +
                "    function initFieldSet(datas) {\n" +
                "        var title = \"\";\n" +
                "        if (datas.operate == \"add\") {\n" +
                "            title += \"新增页面\";\n" +
                "        } else if (datas.operate == \"edit\") {\n" +
                "            title += \"编辑页面\";\n" +
                "        } else if (datas.operate == \"view\") {\n" +
                "            title += \"详情页面\";\n" +
                "        }\n" +
                "        return title;\n" +
                "    }\n" +
                "\n" +
                "    /**\n" +
                "     *功能说明 给控件绑定blur事件\n" +
                "     */\n" +
                "    function bindBlurFunction() {\n" +
                "        $detailContent.find(\".validate\").blur(function(e) {\n" +
                "            var target = e.currentTarget;\n" +
                "            $(target).removeAttr(\"data-original-title\").removeClass(\"ver-error\");\n" +
                "            var val = $(target).val();\n" +
                "            if (val == \"\") {\n" +
                "                $(target).addClass(\"ver-error\").attr(\"data-original-title\", placeholder + \"不存在\").tooltip();\n" +
                "            }\n" +
                "        });\n" +
                "    }\n" +
                "\n" +
                "    /**\n" +
                "     *功能说明 重置\n" +
                "     */\n" +
                "    function reset(datas) {\n" +
                "        //初始化控件提示状态\n" +
                "        initControlsTipState();\n" +
                "        $detailContent.find(\"input[type='text']\").val(\"\");\n" +
                "    }\n" +
                "\n" +
                "    function save(datas) {\n" +
                "        var submitData = createData(datas);\n" +
                "        if (!checkDataValid()) {\n" +
                "            return;\n" +
                "        }\n" +
                "        tn.ajax.request({\n" +
                "            type: \"POST\",\n" +
                "            data: submitData,\n" +
                "            url: getAction().save,\n" +
                "            listener: {\n" +
                "                success: function(json) {\n" +
                "                    if (tn.type.isNull(json) || !tn.type.isObject(json) || tn.type.isNull(json.success)) {\n" +
                "                        //self.noty.error();\n" +
                "                        alert(json.msg);\n" +
                "                    }\n" +
                "                    if (json.success === false) {\n" +
                "                        //self.noty.error(json.msg);\n" +
                "                        alert(json.msg);\n" +
                "                    }\n" +
                "                    if (json.success === true) {\n" +
                "                        config.noty.info(\"保存成功\");\n" +
                "                        closeWindow();\n" +
                "                    }\n" +
                "                }\n" +
                "            }\n" +
                "        });\n" +
                "    }\n" +
                "\n" +
                "    function update(datas) {\n" +
                "        var submitData = createData(datas);\n" +
                "        tn.ajax.request({\n" +
                "            type: \"POST\",\n" +
                "            data: submitData,\n" +
                "            url: getAction().update,\n" +
                "            listener: {\n" +
                "                success: function(json) {\n" +
                "                    if (tn.type.isNull(json) || !tn.type.isObject(json) || tn.type.isNull(json.success)) {\n" +
                "                        //self.noty.error();\n" +
                "                        alert(json.msg);\n" +
                "                    }\n" +
                "                    if (json.success === false) {\n" +
                "                        //self.noty.error(json.msg);\n" +
                "                        alert(json.msg);\n" +
                "                    }\n" +
                "                    if (json.success === true) {\n" +
                "                        config.noty.info(\"修改成功\");\n" +
                "                        closeWindow();\n" +
                "                    }\n" +
                "                }\n" +
                "            }\n" +
                "        });\n" +
                "    }\n" +
                "\n" +
                "    /*功能说明 关闭窗口\n" +
                "     */\n" +
                "    function closeWindow() {\n" +
                "        win.hide();\n" +
                "        config.reloadList.call(config);\n" +
                "    }\n" +
                "\n" +
                "\n" +
                "    /**\n" +
                "     *功能说明 页面显示数据\n" +
                "     */\n" +
                "    function showData(data) {\n";
        for (String field: fieldMap.keySet()) {
            formJsStr += "        $detailContent.find(\"input[name='" + field + "']\").val(data." + field + ");\n";
        }

        formJsStr += "    }\n" +
                "\n" +
                "    function checkDataValid(){\n";

        for (String field: fieldMap.keySet()) {
            formJsStr += "        var " + field + " = $detailContent.find(\"input[name='" + field + "']\").val();\n" +
                         "        if (" + field + " == \"\") {\n" +
                         "            $detailContent.find(\"input[name='" + field + "']\").addClass(\"ver-error\").attr(\"data-original-title\",\"" + fieldMap.get(field) + "不能为空\").tooltip();\n" +
                         "        }\n";
        }

        formJsStr +=  "        if ($detailContent.find(\".ver-error\").length>0) {\n" +
                "            return false;\n" +
                "        }\n" +
                "        return true;\n" +
                "    }\n" +
                "\n" +
                "    /**\n" +
                "     *功能说明 构造保存数据\n" +
                "     */\n" +
                "    function createData(datas) {\n" +
                "        var submitData = config.util.form.get($detailContent);\n" +
                "        if (datas.operate == 'edit') {\n" +
                "            submitData.id = datas.data.id;\n" +
                "        }\n" +
                "        submitData.opUid = tn.cookie.get(\"honeydukesUid\");\n" +
                "        submitData.opName = tn.Base64.decode(tn.cookie.get(\"honeydukesUname\"));\n" +
                "        return submitData;\n" +
                "    }\n" +
                "\n" +
                "    /**\n" +
                "     *功能说明 初始化控件提示状态\n" +
                "     */\n" +
                "    function initControlsTipState() {\n" +
                "        $detailContent.find(\"input\").removeClass('ver-error').removeAttr('data-original-title');\n" +
                "    }\n" +
                "    /**\n" +
                "     *页面AJAX请求URL统一处理\n" +
                "     */\n" +
                "    function getAction() {\n" +
                "        if (config.devcfg) {\n" +
                "            return {\n" +
                "                save: config.protectedSystem.server.ASR + \"" + fileNameLower + "/save\",\n" +
                "                update: config.protectedSystem.server.ASR + \"" + fileNameLower + "/update\",\n" +
                "            };\n" +
                "        } else {\n" +
                "            return {\n" +
                "                save: config.protectedSystem.server.ASR + \"" + fileNameLower + "/save\",\n" +
                "                update: config.protectedSystem.server.ASR + \"" + fileNameLower + "/update\",\n" +
                "            };\n" +
                "        }\n" +
                "\n" +
                "    }\n" +
                "}\n";

        File formJsFile = new File(modelConfig.getTargetProject() + "\\" + fileName + "Create.js");
        if (null != formJsStr && !"".equals(formJsStr)) {
            try {
                FileWriter fw = new FileWriter(formJsFile);
                fw.write(formJsStr);
                fw.flush();
                fw.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

	public void setProgressCallback(ProgressCallback progressCallback) {
        this.progressCallback = progressCallback;
    }

    public void setIgnoredColumns(List<IgnoredColumn> ignoredColumns) {
        this.ignoredColumns = ignoredColumns;
    }

    public void setColumnOverrides(List<ColumnOverride> columnOverrides) {
        this.columnOverrides = columnOverrides;
    }
}
