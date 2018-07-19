[#-- @ftlvariable name="request" type="droute.Request" --]
[#macro page title]
<!doctype html>
<html>
<head lang="en">
    <meta charset="utf-8">
    <base href="${request.contextPath()}">
    <title>${title}</title>
    <link rel="stylesheet" href="webjars/bootswatch-paper/3.3.4%2B1/css/bootstrap.min.css">
    <link rel="stylesheet" href="assets/butterflynet.css">
</head>
<body>
    <div class="container main">
        <ul class="nav nav-tabs pull-right">
            [#if user??]<li><a class="disabled"><i class="glyphicon glyphicon-user"></i> ${user.name}</a></li>[/#if]
            <li [#if request.path() = '/']class="active"[/#if]><a href="."><i class="glyphicon glyphicon-th-list"></i> Captures</a></li>
            <li [#if request.path() = '/settings']class="active"[/#if]><a href="settings"><i class="glyphicon glyphicon-cog"></i> Settings</a></li>
        </ul>

        [#nested/]
    </div>
    <div class="bgcredit">
        <a href="http://nla.gov.au/nla.pic-vn3545852">"Papillio excellieus" by Marrianne Campbell<br>nla.pic-vn3545852</a>
    </div>
    <script src="webjars/jquery/2.1.4/jquery.min.js"></script>
    <script src="webjars/bootstrap/3.3.4/js/bootstrap.min.js"></script>
    <script src="webjars/timeago/1.4.1/jquery.timeago.js"></script>
    <script src="webjars/riot/2.2.4/riot%2bcompiler.js"></script>
    <script src="webjars/clipboard/1.3.1/dist/clipboard.js"></script>

    <script src="tags/capture-list.tag" type="riot/tag"></script>

    <script src="assets/EventSource.js"></script>
    <script src="assets/butterflynet.js"></script>
</body>
</html>
[/#macro]