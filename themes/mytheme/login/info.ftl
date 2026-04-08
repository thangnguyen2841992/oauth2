<#import "template.ftl" as layout>
<@layout.registrationLayout; section>

    <#if section == "header">
        INFO

    <#elseif section == "form">
        INFO PAGE

    <#elseif section == "info">
        OK

    </#if>

</@layout.registrationLayout>