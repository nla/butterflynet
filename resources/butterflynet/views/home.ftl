[@page title="Save to web archive"]

<ul class="nav nav-pills pull-right">
    <li><a href="settings"><i class="glyphicon glyphicon-cog"></i> Settings</a></li>
</ul>
<h3>Save to web archive</h3>

<form method="post">
    <input name="csrfToken" value="${csrfToken}" type="hidden" id="csrfTokenField">
    <div class="new-capture-form">
        <input class="url-field" name="url" id="url" type="url" placeholder="URL of a document. e.g. http://example.org/report.pdf">
        <button type="submit" class="btn btn-primary"><i class="glyphicon glyphicon-save"></i> Capture It</button>
    </div>
</form>

<p></p><p></p>

<div id="captureList">
    [#noescape]${tableHtml}[/#noescape]
</div>

[/@page]