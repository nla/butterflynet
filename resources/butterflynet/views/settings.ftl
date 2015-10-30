[#-- @ftlvariable name="allowedMediaTypes" type="java.util.List<String>" --]
[@page "Butterflynet Settings"]

<h3>Settings</h3>

<div class="form-group">
    <h5>Allowed file formats</h5>
    <ul class="list-group">
        [#list allowedMediaTypes as mediaType]
            <li class="list-group-item">
                ${mediaType}
                <form action="settings/allowed-media-types/delete" method="post" class="pull-right">
                    <input name="csrfToken" value="${csrfToken}" type="hidden">
                    <input name="mediaType" value="${mediaType}" type="hidden">
                    <button class="btn btn-default">Remove</button>
                </form>
            </li>
        [/#list]
        <li class="list-group-item">
            <form action="settings/allowed-media-types/create" method="post">
                <input name="csrfToken" value="${csrfToken}" type="hidden">
                <input name="mediaType" style="width: 400px" required>
                <button class="btn btn-default">Add</button>
            </form>
        </li>
    </ul>
</div>


[/@page]