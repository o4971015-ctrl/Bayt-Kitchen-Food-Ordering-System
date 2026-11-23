const fulfilment = document.querySelector('#fulfilment');
if (fulfilment) {
  const address = document.querySelector('#address-field');
  const updateAddress = () => {
    address.hidden = fulfilment.value !== 'Delivery';
    address.querySelector('input').required = !address.hidden;
  };
  fulfilment.addEventListener('change', updateAddress);
  updateAddress();
}
document.querySelector('#back')?.addEventListener('click', () => history.back());
