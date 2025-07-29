---
layout: default
title: Getting Started
---

<p>You can use this system locally, in a Continuous Deployment (CD) pipeline or in an 
orchestration service with an image.</p>

<p>First create a programmatic access token on Snowflake so that you can login without a 
browser. Note that this application is for security administration and will therefore 
require elevated access.</p>

<div class="code-block">
  <div class="code-block-title">Snowflake SQL</div>
{% highlight sql %}
alter user MY_USER add programmatic access token dfence_test
role_restriction = 'ACCOUNTADMIN';
{% endhighlight %}
</div>

<p>Export Environment Variables. These are based on the variables required in 
<a href="https://github.com/zoom/zoom-data-fence/blob/main/example/profiles.yml">example/profiles.yml</a>. You can specify your own variable names
based on your needs when you write your own profile.</p>

<div class="code-block">
  <div class="code-block-title">shell</div>
{% highlight shell %}
export DFENCE_USER="Your Snowflake Login Name"
export DFENCE_TOKEN="Your Programmatic Access Token"
export DFENCE_ACCOUNT="Your Snowflake Account Name"
{% endhighlight %}
</div>

<p>Run a compile command in Docker using the <a href="https://github.com/zoom/zoom-data-fence/tree/main/example">example configuration</a>.</p>

<div class="code-block">
<div class="code-block-title">shell</div>
{% highlight shell %}
docker run -it -v $PWD/example:/example -e DFENCE_TOKEN -e DFENCE_USER -e DFENCE_ACCOUNT \
  --workdir /example zoomvideo/zoom-data-fence \
  compile --var-file env/dev/vars.yml roles
{% endhighlight %}
</div>

<h3>Demo Setup</h3>

<p>In order to demonstrate the tool, let's create some objects to grant permissions on.</p>

<div class="code-block">
<div class="code-block-title">Snowflake SQL</div>
{% highlight sql %}
create database if not exists my_db;
create schema if not exists my_db.my_schema;
create table if not exists my_db.my_schema.my_table(idx integer);
{% endhighlight %}
</div>

<h3>Roles File</h3>

<p>Create a file with a configuration using some databases which are under your control.</p>

<div class="code-block">
  <div class="code-block-title">roles.yml</div>
{% highlight yaml %}
roles:
  rbac-example-role-1:
    name: rbac_example_1
    create: true
    grants:
      - database-name: my_db
        object-type: database
        privileges:
          - usage
      - database-name: my_db
        schema-name: my_schema
        object-type: schema
        privileges:
          - usage
      - database-name: my_db
        schema-name: my_schema
        object-name: my_table
        object-type: table
        privileges:
          - select
  rbac-example-role-2:
    name: rbac_example_2
    create: true
    grants:
      - object-type: role
        privileges:
          - USAGE
        object-name: rbac_example_1
{% endhighlight %}
</div>

<h3>Profiles File</h3>

<p>Next you will need to create a profiles file that you can use to connect to the database.
The profiles file can list multiple environments. This is a file for your filesystem only.</p>

<div class="code-block">
  <div class="code-block-title">profiles.yml</div>
{% highlight yaml %}
default-profile: dev
profiles:
  dev:
    provider-name: SNOWFLAKE
    connection:
      snowflake:
        connection-string: jdbc:snowflake://your-dev-account.snowflakecomputing.com:443
        connection-properties:
          authenticator: externalbrowser
          user: YOUR.USERNAME
          role: SECURITYADMIN
  prod:
    provider-name: SNOWFLAKE
    connection:
      snowflake:
        connection-string: jdbc:snowflake://your-prod-account.snowflakecomputing.com:443
        connection-properties:
          authenticator: externalbrowser
          user: YOUR.USERNAME
          role: SECURITYADMIN
{% endhighlight %}
</div>

<p>Note that to use this tool you will need SECURITYADMIN or a similar role with enough
permissions to create roles and manage grants.</p>

<h3>Execution</h3>

<p>Now you can compile your changes into a plan.</p>

{% highlight shell %}
dfence compile --profile-file local/profiles.yml local/roles.yml
{% endhighlight %}

<p>You should see a set of changes that are planned.</p>

<div class="code-block">
 <div class="code-block-title">stdout</div>
{% highlight yaml %}
---
changes:
- role-creation-statements:
  - "create role if not exists rbac_example_2;"
  role-grant-statements:
  - "grant role rbac_example_1 to role rbac_example_2;"
  role-id: "rdbac-example-role-2"
  role-name: "rbac_example_2"
- role-creation-statements:
  - "create role if not exists rbac_example_1;"
  role-grant-statements:
  - "grant usage on database my_db to role rbac_example_1;"
  - "grant usage on schema my_db.my_schema to role rbac_example_1;"
  - "grant select on table my_db.my_schema.my_table to role rbac_example_1;"
  role-id: "rdbac-example-role-1"
  role-name: "rbac_example_1"
total-roles: 2
total-changes: 2
{% endhighlight %}
</div>

<p>If you would like to execute the changes, you can apply them.</p>

<div class="code-block">
 <div class="code-block-title">shell</div>
{% highlight shell %}
dfence apply --profile-file local/profiles.yml local/roles.yml
{% endhighlight %}
</div>

<p>Now that we have made the changes, we can execute the compile step again. We should see no
changes.</p>

<div class="code-block">
 <div class="code-block-title">shell</div>
{% highlight shell %}
dfence compile --profile-file local/profiles.yml local/roles.yml
{% endhighlight %}
</div>

<div class="code-block">
<div class="code-block-title">stdout</div>
{% highlight yaml %}
---
changes: [ ]
total-roles: 2
total-changes: 0
{% endhighlight %}
</div>

<p>data-fence strives to not run any statements against the database unless they are
actually needed. This reduces noise when one needs to look at system logs to investigate a
change. In addition, note that data-fence will remove grants which are not defined in
the roles file. Let's try manually granting a permission outside the process.</p>

<div class="code-block">
<div class="code-block-title">Snowflake SQL</div>
{% highlight sql %}
grant create schema on database my_db to role rbac_example_1;
{% endhighlight %}
</div>

<p>Now let's run the tool again.</p>

<div class="code-block">
  <div class="code-block-title">shell</div>
{% highlight shell %}
dfence apply --profile-file local/profiles.yml local/roles.yml
{% endhighlight %}
</div>

<p>We now see that we are revoking the rogue grant.</p>

{% highlight yaml %}
---
changes:
- role-creation-statements: []
  role-grant-statements:
  - "revoke create schema on database my_db from role rbac_example_1;"
  role-id: "rdbac-example-role-1"
  role-name: "rbac_example_1"
total-roles: 2
total-changes: 1
{% endhighlight %}

<p>This ability to revoke grants by deleting them from the configuration is a feature.
However, it is important to note that if you are migrating roles to data-fence, you need
to include all the grants already in the backend. Prior to deployment, you should run a
compile command and see no changes. Only at this point should you make additional changes.</p>

<h3>Clean Up</h3>

<p>Now we should clean up the objects that we made in our example.</p>

{% highlight sql %}
use role sysadmin;
drop database if exists my_db;
use role securityadmin;
drop role if exists rbac_example_1;
drop role if exists rbac_example_2;
drop role if exists rbac_example_3;
{% endhighlight %}

<h3>Variables</h3>

<p>Variables can be used in profile files and roles files. Create a variables file like this.</p>

{% highlight yaml %}
env-suffix=dev
src-database-name=src_experimental
sso-finance-data-analyst-role=okta_finance_analyst_dev
{% endhighlight %}

<p>Now you can use these variables in the files.</p>

<div class="code-block">
{% highlight yaml %}
roles:
  rbac-example-role-1:
    name: rbac_example_1${var.env-suffix}
    grants:
      - database-name: ${var.src-database}
        object-type: database
        privileges:
          - usage
      - object-type: role
        object-name: ${var.sso-finance-data-analyst-role}
        privileges:
          - usage
{% endhighlight %}
</div>

<p>You can pass these variables using the `--var-file` option.</p>

<div class="code-block">
{% highlight shell %}
dfence apply --profile-file local/profiles.yml --var-file local/variables-dev.yml local/roles.yml
{% endhighlight %}
</div>

<h3>Wildcard Grants</h3>

<p>Wildcards may be used to perform grants on all future and existing objects within a schema
or database. For example, this permission will grant `select` on all future and existing
tables within database `my_db` to role `rbac_example_1`.</p>

<div class="code-block">
{% highlight yaml %}
roles:
  rbac-example-role-1:
    name: rbac_example_1
    grants:
      - database-name: my_db
        object-name: "*"
        object-type: table
        privileges:
          - select
{% endhighlight %}
</div>

<p>There are times whwn you may wish to only affect future roles or current roles. By 
default, both are included in a wildcard grant.</p>

<p>For example, if you wish to leave existing grants alone but you want any new tables to 
have the grant, then you would set `include-future: true` and `include-all: false`.</p>

<div class="code-block">
  <div class="code-block-title">roles.yml</div>
{% highlight yaml %}
roles:
  rbac-example-role-1:
    name: rbac_example_1
    grants:
      - database-name: my_db
        object-name: "*"
        object-type: table
        include-future: true
        include-all: false
        privileges:
          - select
{% endhighlight %}
</div>

<h3>Ownership Grants</h3>

<p>All ownership grants are implemented in
<a href="https://docs.snowflake.com/en/sql-reference/sql/grant-ownership">Snowflake with Copy Current Grants</a>.
In addition, when an ownership grant is destroyed, rather than being revoked it is granted
to the configured SECURITYADMIN role with copy current grants. This prevents other grants on the
object from being destroyed during the change of ownership.</p>

<p>However, such an outcome is not desirable. When such ownership revocations occur, they 
are an indication that object ownership needs to be explicitly assigned to a role in the 
configuration.</p>

<h3>Importing Roles</h3>

<p>You can import existing roles in order to make a new playbook.</p>

<div class="code-block">
{% highlight yaml %}
roles:
  rbac-example-role-1:
    name: rbac_example_1
    grants:
      - database-name: my_db
        object-type: database
  {% endhighlight %}
</div>

<div class="code-block">
  <div class="code-block-title">shell</div>
{% highlight shell %}
dfence import-roles role_a role_b
{% endhighlight %}
</div>

<p>In addition, it is possible to search for roles based on a regular expression. For
example, in order to find all roles which start with UR_ or SR_, use the following
command.</p>

<div class="code-block">
  <div class="code-block-title">shell</div>
{% highlight shell %}
dfence import-roles --patterns '^UR_.*' '^SR_.*'
{% endhighlight %}
</div>

<h3>Multiple Roles Files</h3>

<p>Multiple role files can be used by providing a directory instead of a file path. If a
directory is provided, all files with a "yml" or "yaml" file extension will be discovered.</p>

<h3>Granting Permissions To Roles Created Another Way</h3>

<p>Most of the time, it is best to have all roles completely managed by the RBAC tool.
However, there are times, such as SCIM provisioning, when it is necessary to perform
grants to roles that are created another way. In these cases, we want to make sure that if
the role does not exist, we do not create it and thus cause problems with ownership or
mask problems in the provisioning system. The `create` property can be set to false if you
do not desire to create a role.</p>

<div class="code-block">
  <div class="code-block-title">roles.yml</div>
{% highlight yaml %}
roles:
  rbac-example-role-1:
    name: rbac_example_1
    create: false
    grants:
      - database-name: my_db
        object-type: database
        privileges:
          - usage
{% endhighlight %}
</div>

<h3>Granting Permissions To Roles Without Revoking Grants Provided Another Way</h3>

<p>While it is best for data-fence to fully own the permissions for a role, in some cases,
it is necessary to use the tool to grant permissions without revoking the permissions
granted another way. This may be necessary while migrating ownership of objects or while
migrating from another system. In these cases, the `revoke-other-grants`
attribute may be set to `false`.</p>

<div class="code-block">
  <div class="code-block-title">roles.yml</div>
{% highlight yaml %}
roles:
  rbac-example-role-1:
    name: rbac_example_1
    revoke-other-grants: false
    grants:
      - database-name: my_db
        object-type: database
        privileges:
          - usage
{% endhighlight %}
</div>
