[@page title="Save to web archive"]

<h3>Save to web archive</h3>

<form method="post">
    <input name="csrfToken" value="${csrfToken}" type="hidden">
    <fieldset>
        <input name="url" id="url" type="url" style="width:920px" placeholder="URL of a document. e.g. http://example.org/report.pdf">
        <button type="submit">Archive!</button>
    </fieldset>
</form>

<p></p><p></p>
<table class="table">
    <colgroup>
        <col>
        <col>
        <col>
        <col width="200px">
    </colgroup>
    <thead>

    </thead>
    <tbody>
        [#list captures as capture]
            <tr>
                <td>${capture.url}<br>
                    [#if capture.state == 0]
                    <div class="progress">
                        <div class="progress-bar progress-bar-striped active" role="progressbar" aria-valuenow="45" aria-valuemin="0" aria-valuemax="100" style="width: 45%">
                        </div>
                    </div>
                    [/#if]

                </td>
                <td></td>
                <td>${capture.getStateName()} [#if capture.status != 0](${capture.status!""} ${capture.reason!""})[/#if]</td>
                <td><abbr class="timeago" title="${capture.started?string.iso}">${capture.started}</abbr></td>
            </tr>
        [/#list]
        <!--
                <button><i class="glyphicon glyphicon-copy"></i> Copy</button>
        -->
    </tbody>
</table>


[/@page]