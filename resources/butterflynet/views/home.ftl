[@page title="Save to web archive"]


<h3>Save to web archive</h3>

<p>This tool captures standalone documents (PDF, RTF, Office) and makes them available immediately through
the <a href="https://webarchive.nla.gov.au/">Australian Web Archive</a>.</p>

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