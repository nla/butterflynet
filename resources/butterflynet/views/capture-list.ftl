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
    [#list captureProgressList as progress]
    [#assign capture=progress.capture]
    <tr>
        <td>${capture.url}<br>
            [#if progress.length > 0 && progress.position < progress.length]
            <div class="progress">
                <div class="progress-bar progress-bar-striped active" role="progressbar" aria-valuenow="${progress.percentage}" aria-valuemin="0" aria-valuemax="100" style="width: ${progress.percentage}%">
                </div>
            </div>
            ${progress.position} / ${progress.length}
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