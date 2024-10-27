Behaviour.specify("[data-type='gcloud-provision']", 'gcloud-provision', -99, function(e) {
  e.addEventListener("click", function (event) {
    const form = document.getElementById(e.dataset.form);
    form.querySelector("[name='configuration']").value = e.dataset.url;
    buildFormTree(form);
    form.submit();
  });
});
