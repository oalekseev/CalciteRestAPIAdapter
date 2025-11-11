# CalciteRestAPIAdapter

CalciteRestAPIAdapter is designed to retrieve data from REST services using standard SQL syntax. It uses the [Apache Calcite](https://calcite.apache.org/) framework, which enables the creation of adapters for various data sources via the JDBC interface.

The adapter provides SQL access to REST APIs, with configuration details defined in XML files. Each XML file describes the service, its tables, fields, data types, and the mapping between REST responses and SQL table fields.

Below is an example XML file for REST API – `OrdersService` with `users` and `orders` tables:

```xml
<service>
  <dataSourceName>OrdersService</dataSourceName>
  <schemaName>v1.0</schemaName>
  <description>OrdersService v1.0</description>

  <requestData>
      <addresses>http://localhost:8080,http://orders.company.org:8080</addresses>
      <connectiononTimeout>10</connectionTimeout>
      <responseTimeout>30</responseTimeout>
      <method>POST</method>
      <url>/api</url>
      <body><![CDATA[...]]></body>
      <pageStart>1</pageStart>
      <pageSize>1000</pageSize>
      <headers>
        <header>
          <key>Content-type</key>
          <value>${content-type}</value>
        </header>
      </headers>
  </requestData>
  <tables>
    <table>
      <name>users</name>
      <rootJsonpath>$</rootJsonpath>
      <parameters>
        <parameter>
          <name>long</name>
          <dbType>number</dbType>
          <jsonpath>id</jsonpath>
          <type>RESPONSE</type>
        </parameter>
        <parameter>
          <name>name</name>
          <dbType>string</dbType>
          <jsonpath>name</jsonpath>
          <type>BOTH</type>
        </parameter>
        <parameter>
          <name>last_name</name>
          <dbType>string</dbType>
          <jsonpath>lastName</jsonpath>
          <type>BOTH</type>
        </parameter>
        <parameter>
          <name>age</name>
          <dbType>int</dbType>
          <jsonpath>age</jsonpath>
          <type>BOTH</type>
        </parameter>
      </parameters>
    </table>
    <table>
      <name>orders</name>
      <rootJsonpath>$</rootJsonpath>
      <parameters>
        <parameter>
          <name>id</name>
          <dbType>long</dbType>
          <jsonpath>id</jsonpath>
          <type>RESPONSE</type>
        </parameter>
        <parameter>
          <name>type</name>
          <dbType>string</dbType>
          <jsonpath>type</jsonpath>
          <type>BOTH</type>
        </parameter>
        <parameter>
          <name>user_id</name>
          <dbType>long</dbType>
          <jsonpath>userId</jsonpath>
          <type>BOTH</type>
        </parameter>
        <parameter>
          <name>price</name>
          <dbType>double</dbType>
          <jsonpath>price</jsonpath>
          <type>RESPONSE</type>
        </parameter>
      </parameters>
    </table>
  </tables>
</service>
```

### Where:

- **service** – service description:
    - `dataSourceName` – service name (can be treated as schema)
    - `schemaName` – schema name
    - `description` – service description
- **requestData** – request parameters:
    - `addresses` – list of addresses; if first connection fails within connection-timeout, try next. Errors if all fail. Includes HTTP/HTTPS protocol.
    - `connectionTimeout` – time for connection establishment
    - `responseTimeout` – server response timeout, errors if not met
    - `method` – HTTP method: POST/GET
    - `url` – URL path without host. Actual URL = addresses[n] + url. URL can have parameters.
    - `body` – request body template; supports macros (see below)
    - `pageStart` – initial page number (0 or 1)
    - `pageSize` – paging: 0–off (all records via one REST call), 1..n–on (acts like SQL limit)
    - `headers` – list of HTTP headers; `value` supports macro substitution (for example, {content-type} can be application/xml or application/json)
- **tables** – schema description of tables, fields/types:
    - **table** – table definition
        - `name` – table name
        - `rootJsonpath` – JSONPath to the array of elements
        - `parameters` – list of all table fields
            - **parameter** – field definition:
                - `name` – field name
                - `dbType` – field type: `boolean`, `byte`, `char`, `short`, `int`, `long`, `float`, `double`, `string`, `date`, `time`, `timestamp`, `uuid`
                - `jsonpath` – relative path from rootJsonpath to field in each array element
                - `type` – field type: `REQUEST` (query only), `RESPONSE` (returned only) and `BOTH` (both - request and response)

Example: if page-start=0, page-size=100, then macro `${limit}=100`, and macro `${offset}` will be: 0, 100, 200,... on each REST call. The page is `(offset / limit)?int`.  
Request parameters (`type=REQUEST`) are critical to limit REST response volume; make sure REST can restrict results, if not, it may return all data.

---

## Request body
### REST services without support for DNF (Disjunctive Normal Form)  
In these services, filtering supports only simple criteria combined through logical AND, and only the equality operator `=` is allowed.  
The request is formed by simply listing fields with the required values. If the incoming filters contain any other operator or attempt to use logical OR, the request is aborted with an error.  
Thus, the service accepts only exact values for fields, for example:

```json
{
   "name": "users",
   "page": 1,
   "limit": 1000,
   "name": "Alice",
   "age": "21"
}
```

In this case, there are no nested filter groups; all filtering is a flat set of attributes - `name` and `age`. Below Freemarker template that generates a simple JSON with flat parameters, checking that the operator is only "=" and that there are no OR groups.

```xml
<![CDATA[{
    "name": "${name}",
    "limit": ${limit},
    "page": ${(offset / limit)?int}
    <#if filters?has_content>,
        <#if (filters?size > 1)><#stop "Error: Rest service does not support OR operators"></#if>
        <#list filters[0] as criterion>
            <#if (criterion.operator != '=')><#stop "Error: Only '=' operator is supported. Found operator: '${criterion.operator}'"></#if>
            "${criterion.name}": "${criterion.value}"
            <#if criterion?has_next>, </#if>
        </#list>
    </#if>
}]]>
```

### REST services with support for DNF (Disjunctive Normal Form)  
Such services can handle complex filter structures in the form of a disjunction (logical OR) of groups of conditions, where inside each group, conditions are combined through conjunction (logical AND).  
The use of different comparison operators is allowed, including `>=`, `=`, and others.  
The request structure contains two key fields — `where` with a group of conditions combined by AND, and `or`, which is an array of groups of conditions combined by OR, for example:

```json
{
   "name": "users",
   "page": 1,
   "limit": 1000,
   "where": [
       { "name": "name", "operator": "=", "value": "Alice" },
       { "name": "age", "operator": ">=", "value": "21" }
   ],
   "or": [
       [
           { "name": "name", "operator": "=", "value": "Bob" },
           { "name": "age", "operator": ">=", "value": "21" }
       ],
       [
           { "name": "name", "operator": "=", "value": "Martin" },
           { "name": "age", "operator": ">=", "value": "21" }
       ]
   ]
}
```

This allows expressing complex filters combining AND and OR. Below Freemarker template that builds a complex JSON with `where` for the first AND group and `or` for the other groups, supporting operators like `=`, `>=`, etc.

```xml
<![CDATA[{
    "name": "${name}",
    "limit": ${limit},
    "page": ${(offset / limit)?int}
    <#if filters?has_content>
        "where": [
            <#-- Take the first group (AND inside) -->
            <#list filters[0] as criterion>
                {
                    "name": "${criterion.name}",
                    "operator": "${criterion.operator}",
                    "value": "${criterion.value}"
                }<#if criterion?has_next>,</#if>
            </#list>
        ]
        <#-- If there are additional groups (OR), put them in "or" -->
        <#if filters?size > 1>
        ,
        "or": [
            <#list filters?seq[1..] as orGroup>
                [
                    <#list orGroup as criterion>
                        {
                            "name": "${criterion.name}",
                            "operator": "${criterion.operator}",
                            "value": "${criterion.value}"
                        }<#if criterion?has_next>,</#if>
                    </#list>
                ]<#if orGroup?has_next>,</#if>
            </#list>
        ]
        </#if>
    </#if>
}]]>
```

---

## Macro Substitutions

Macros can be used in URL, headers, and body.  
The [FreeMarker](https://freemarker.apache.org/) engine powers macro support. 

Built-in macros:

- `name` – table name
- `user` – username used in JDBC url
- `password` – password used in JDBC url
- `offset` – auto-incremented by page-size for each REST call
- `limit` – page size, defined in XML as page-size
- `projects` – key-value structure holding all fields used in SELECT сlause of query (`${projects.<name>}`)
- `filters` – list of DNF (disjunctive normal form) condition groups

Also, any parameters passed in the Calcite JDBC connection URL are available.

---

## WHERE Clause

The SQL query can use filtering, which Calcite will apply to REST data.  
If REST service supports filters, they can be sent in requests (see main example); all filters are in the `filters` parameter.

Operators in SQL can be: `=`, `>`, `<`, `>=`, `<=`, `<>`.  
If the REST service can't filter, simply don't use filters in the template; Calcite will filter locally.

---

## Field Selection

You may select individual fields in SQL, e.g.:

```
SELECT name1, name2 FROM users
```

All selected fields will show up in the `projects` macro (keys are field names).

---

## FreeMarker Syntax

See [Apache Freemarker documentation](https://freemarker.apache.org/docs/dgui_template_exp.html#dgui_template_exp_arit). Below are the basics:

| Description                             | Usage                                                                             |
|-----------------------------------------|-----------------------------------------------------------------------------------|
| Simple parameter usage (e.g. limit)     | `${limit}`                                                                        |
| Key-value structure (e.g. projects map) | `${projects.name}`<br/>`${projects.age}`                                          |
| Assign variable (take first list group) | `<#assign firstGroup = filters[0] />`                                             |
| Check if list has content               | `<#if filters?has_content>...</#if>`                                              |
| Access criterion field values           | `${criterion[0].name}`<br/>`${criterion[0].operator}`<br/>`${criterion[0].value}` |
| Check if list size > 1                  | `<#if (filters?size > 1)>...</#if>`                                               |
| Iterate list (`?has_next` for comma)    | `<#list filters as orGroup>{...}<#if orGroup?has_next>,</#if></#list>`          |
| Integer division                        | `${(offset / limit)?int}`                                                         |
| Return error on condition               | `<#if (filters?size > 1)><#stop "Error: ..."></#if>`                              |

---

## Paging Mechanism

Paging is enabled by `page-size > 0`, resulting in multiple REST calls with macros `${limit}` and `${offset}`.  
Paging stops when a REST reply contains less elements than page-size.

:warning:  
Current implementation does **not** natively support SQL OFFSET and LIMIT mapped to REST.  
LIMIT is supported via paged requests until enough records are retrieved. OFFSET in SQL is handled only client-side after retrieving all records from REST.

---

## SQL Query Examples

You can use any valid SQL query, including with WHERE, CTEs, JOINs, etc. See https://calcite.apache.org/docs/reference.html

You may select specific fields:
If selecting all table columns, use `*` or specify columns in the same order as in XML (related to the characteristics of Calcite itself).

```sql
SELECT name, last_name FROM users
```

For REST services without support for DNF

```sql
SELECT * FROM users u, orders o 
WHERE 
o.start_time = '2023-01-01 00:00:00' AND o.end_time = '2023-01-15 00:00:00' AND
u.id = o.user_id
```

For REST services with support for DNF

```sql
SELECT * FROM users u, orders o
WHERE 
users.age >= 21 AND
o.time >= '2023-01-01 00:00:00' AND o.time < '2023-01-15 00:00:00' AND
u.id = o.user_id
```

If you need to join tables from REST service (maybe even from different REST services (defined in other xml-config files) specify schema if needed). REST calls will be made for each table, and JOIN will occur after fetching.

```sql
WITH constants AS (
    SELECT 'Alice' AS name_const, 21 AS age_const
)
SELECT *
FROM users u
JOIN constants c ON u.name = c.name_const
WHERE u.age >= c.age_const
```

```sql
SELECT * FROM users
WHERE (name = 'Bob' OR age = 23)
      AND (name = 'Martin' OR (age = 21 AND name <> 'Alice'))
```


## Usage

In your app you should create Calcite connection

```java
private final static String DEFAULT_SCHEMA_NAME = "rest";
private final static String CONTEXT_NAME = "context";

System.setProperty("saffron.default.charset", "UTF-8");
System.setProperty("saffron.default.nationalcharset", "UTF-8");
System.setProperty("saffron.default.collation.name", "UTF-8$en_US");

public static void main(String[] args) {
  try (Connection connection = getConnection();
       Statement statement = connection.createStatement();) {
      ResultSet rs = statement.executeQuery("SELECT * FROM users u");
      while (rs.next()) {
          String userName = rs.getString("name");
          int userAge = rs.getInt("age");
          //get other fields
      }
      //Do some work

  } catch (Exception e) {
      throw new RuntimeException(e);
  }
}

private Connection getConnection() {
        System.setProperty("saffron.default.charset", ENCODING);
        System.setProperty("saffron.default.nationalcharset", ENCODING);
        System.setProperty("saffron.default.collation.name", ENCODING + "$en_US");

        Map<String, TemplateModel> macrosValuesMap = new HashMap<>();
        macrosValuesMap.put("REST_API_VERSION", new SimpleScalar("v1.0"));

        Properties properties = new Properties();
        properties.setProperty(CalciteConnectionProperty.FUN.camelName(), "all");
        properties.setProperty(CalciteConnectionProperty.CASE_SENSITIVE.camelName(), "false");
        properties.setProperty(CalciteConnectionProperty.QUOTED_CASING.camelName(), Casing.UNCHANGED.name());
        properties.setProperty(CalciteConnectionProperty.UNQUOTED_CASING.camelName(), Casing.UNCHANGED.name());
        properties.setProperty(CalciteConnectionProperty.LEX.camelName(), "JAVA");

        DriverManager.registerDriver(new org.apache.calcite.jdbc.Driver());
        CalciteConnection calciteConnection = DriverManager.getConnection("jdbc:calcite:", properties).unwrap(CalciteConnection.class);
        calciteConnection.setSchema(DEFAULT_SCHEMA_NAME);

        Map<String, Object> contextMap = new HashMap<>();
        contextMap.put(CONTEXT_NAME, macrosValuesMap);
        
        return calciteConnection;
    }
```

Адаптер собирается как билиотека и кладется в classpath вашего приложения. 
Также туда должны попасть необходимые билиотеки:
- Calcite Core 1.37
- Calcite Linq4j 1.37
- Apache Calcite Avatica 1.25
- Apache Calcite Avatica Core 1.25
- Protocol Buffers 3.24
- OpenCensus API 0.31
- OpenCensus Contrib HTTP Client 0.31
- GRPC Context 1.19
- JTS Core 1.19
- Janino 3.1
- Commons Compiler 3.1
- Json Path 2.8
- Json Smart 2.4
- Accessors Smart 2.4
- jackson-annotations 2.12
- jackson-core 2.12
- jackson-databind 2.12
- jackson-dataformat-xml 2.12
- jackson-dataformat-yaml 2.12
- jackson-module-jaxb-annotations 2.6
- Google Guava 32.1
- Guava InternalFutureFailureAccess and InternalFutures 1.0
- Apache Freemarker 2.3
- Apache Commons Math 3.6
- Apache Commons Codec 1.6
- Apache Commons Text 1.3
- Apache commons Lang 3.7
- Apache Commons io 2.11
- stax2-api 4.2

