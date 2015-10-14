[@page title="Save to web archive"]

<h3>Save to web archive</h3>

<form method="post">
    <input name="csrfToken" value="${csrfToken}" type="hidden" id="csrfTokenField">
    <fieldset>
        <input name="url" id="url" type="url" style="width:920px" placeholder="URL of a document. e.g. http://example.org/report.pdf">
        <button type="submit">Archive!</button>
    </fieldset>
</form>

<p></p><p></p>

<div id="captureList">
    [#noescape]${tableHtml}[/#noescape]
</div>

[/@page]