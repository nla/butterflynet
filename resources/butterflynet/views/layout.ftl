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
        [#nested/]
    </div>
    <div class="bgcredit">
        <a href="http://nla.gov.au/nla.pic-vn3545852">"Papillio excellieus" by Marrianne Campbell<br>nla.pic-vn3545852</a>
    </div>
    <script src="webjars/jquery/2.1.4/jquery.min.js"></script>
    <script src="webjars/bootstrap/3.3.4/js/bootstrap.min.js"></script>
    <script src="webjars/timeago/1.4.1/jquery.timeago.js"></script>
    <script src="webjars/riot/2.2.4/riot%2bcompiler.js"></script>

    <script src="tags/capture-list.tag" type="riot/tag"></script>

    <script src="assets/EventSource.js"></script>
    <script src="assets/butterflynet.js"></script>
</body>
</html>
[/#macro]